/*
 * Copyright (C) 2008 Idega Software hf. All Rights Reserved.
 *
 * This software is the proprietary information of Idega hf.
 * Use is subject to license terms.
 */

package com.idega.ascertia;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.faces.context.FacesContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.bouncycastle.util.encoders.Base64;
import org.springframework.beans.factory.annotation.Autowired;

import com.ascertia.adss.client.api.DocumentHashingRequest;
import com.ascertia.adss.client.api.DocumentHashingResponse;
import com.ascertia.adss.client.api.EmptySignatureFieldRequest;
import com.ascertia.adss.client.api.EmptySignatureFieldResponse;
import com.ascertia.adss.client.api.SignatureAssemblyRequest;
import com.ascertia.adss.client.api.SignatureAssemblyResponse;
import com.ascertia.adss.client.api.SigningRequest;
import com.idega.block.process.variables.Variable;
import com.idega.block.process.variables.VariableDataType;
import com.idega.business.IBOLookup;
import com.idega.core.file.util.MimeTypeUtil;
import com.idega.jbpm.exe.BPMFactory;
import com.idega.jbpm.exe.TaskInstanceW;
import com.idega.jbpm.variables.BinaryVariable;
import com.idega.jbpm.variables.VariablesHandler;
import com.idega.presentation.IWContext;
import com.idega.slide.business.IWSlideService;
import com.idega.util.CoreConstants;
import com.idega.util.expression.ELUtil;
import com.idega.webface.WFUtil;

public class AscertiaServlet extends HttpServlet {
	/**
	 * 
	 * Contents' type of http response
	 */

	private static final String CONTENT_TYPE = "text/xml";
	
	private Logger logger = Logger.getLogger(AscertiaServlet.class.getName());
	
	
	@Autowired
	private BPMFactory bpmFactory;
	
	@Autowired
	private AscertiaDataPull ascertiaDataPull;
	
	public AscertiaDataPull getAscertiaDataPull() {
		return ascertiaDataPull;
	}

	public void setAscertiaDataPull(AscertiaDataPull ascertiaDataPull) {
		this.ascertiaDataPull = ascertiaDataPull;
	}

	public BPMFactory getBpmFactory() {
		return bpmFactory;
	}

	public void setBpmFactory(BPMFactory bpmFactory) {
		this.bpmFactory = bpmFactory;
	}

	// Initialize global variables

	public void init(ServletConfig a_objServletConfig) throws ServletException {
		
		ELUtil.getInstance().autowire(this);
		
		super.init(a_objServletConfig);

		System.out.println("Asctertia servlet started");

	}

	// Process the HTTP Get request

	

	public void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		System.out
				.println("GoSign Request Has Been Recieved in the doGet Method...");

		response.setContentType("text/html");

		getServletContext().getRequestDispatcher("/gosign.html").include(
				request, response);

	}

	// Process the HTTP Post request

	public void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		logger.log(Level.INFO,"GoSign Request Has Been Recieved in the doPost Method...");

		/* Reading parameters' values from web.xml file */

		/**
		 * 
		 * URL of deployed PDF Signer Server
		 */
		// TODO: get dinamically
		String ADSS_URL = "http://82.221.28.123/adss/signing/";// getServletContext
		// ().
		// getInitParameter
		// ("ADSS_URL");
		/**
		 * 
		 * URL of deployed PDF Signer Server, where Document Hashing's
		 * request(s) will be sent
		 */

		String HASHING_URL = ADSS_URL + "dhi";

		/**
		 * 
		 * URL of deployed PDF Signer Server, where Signature Assembly's
		 * request(s) will be sent
		 */

		String ASSEMBLY_URL = ADSS_URL + "sai";

		/**
		 * 
		 * User name, registered on PDF Signer Server
		 */

		String ORIGINATOR_ID = getServletContext().getInitParameter(
				"ORIGINATOR_ID");

		/**
		 * 
		 * Signing profile id that will be used to sign the existing user's
		 * blank signature field
		 */

		// TODO: get dinamically
		String USER_PROFILE_ID = "adss:signing:profile:007";//getServletContext(
		// )
		// .getInitParameter
		// (
		// "USER_PROFILE_ID"
		// );

		/* Printing the parameters' values on console */
		
		/*
		System.out.println("HASHING_URL : " + HASHING_URL);

		System.out.println("ASSEMBLY_URL : " + ASSEMBLY_URL);

		System.out.println("ORIGINATOR_ID : " + ORIGINATOR_ID);

		System.out.println("USER_PROFILE_ID : " + USER_PROFILE_ID);
		*/
		/**
		 * 
		 * variable that is used to detect, the server has been hit first or
		 * second time
		 */

		boolean isFirstTime = false;

		/**
		 * 
		 * Certificate that will be used for producing user's signature
		 */

		String certificate = null;

		/**
		 * 
		 * PKCS#7 bytes
		 */

		byte[] b_pkcs7 = null;

		/**
		 * 
		 * Http session, used to keep the pdf document's id in record during the
		 * complete signing request
		 */

		HttpSession session =  request.getSession(true);

		// String adss_signing_profile = null;

		/**
		 * 
		 * Variable that is used to detect, the response from the PDF Signer
		 * Server is successfull or not
		 */

		boolean isResponseSuccessfull = false;

		try {

			/* The midware web application has been hit/called first time */

			if (request.getParameter("serverHit") != null &&

			request.getParameter("serverHit").equalsIgnoreCase("FIRST")) {

				isFirstTime = true;

				/*
				 * Reading the parameters' values from http request and printing
				 * them on console
				 */

				certificate = request.getParameter("certificate");

				/*
				 * Sometimes when user's selected certificate(Base 64 encoded)
				 * is posted on http request,
				 * 
				 * '+' character is converted into ' ' character, so replacing
				 * it back with the original
				 * 
				 * character ' ' to get the original certificate's bytes
				 */

				certificate = certificate.replaceAll(" ", "+");

				logger.log(Level.INFO,"str_certificate: " + certificate);

			}

			// Get the body of the HTTP request

			logger.log(Level.INFO,"Ready to download contents having length = " +

			request.getContentLength() + " bytes");

			/**
			 * 
			 * Response that will be recieved from PDF Signer Server
			 */

			byte[] soapResponseBytes = null;
			byte[] signedDocument = null;

			byte rawPdfFile[];

			String errorMessage = "Error message hasn't been set properly.";

			/* Web application has been called first time, for Document Hash */

			if (isFirstTime) {
				logger.log(Level.INFO,"Server has been hit first time.");
				InputStream inputStream;
				// getting pdf from url
				String documentURL = request
						.getParameter(AscertiaConstants.DOCUMENT_URL_ID);

				FacesContext fctx = WFUtil.createFacesContext(request.getSession().getServletContext(), request, response);
				IWContext iwc = IWContext.getIWContext(fctx);
				
				
				String fileName; 
				if (documentURL == null || documentURL.trim().equals("")) {

					
					Integer variableHash = Integer
							.valueOf(request
									.getParameter(AscertiaConstants.PARAM_VARIABLE_HASH));
					Long taskInstanceId = Long
							.valueOf(request
								.getParameter(AscertiaConstants.PARAM_TASK_ID));

					VariablesHandler variablesHandler = getVariablesHandler(iwc
							.getServletContext());

					BinaryVariable binaryVariable = getBinVar(variablesHandler,
							taskInstanceId, variableHash);

					inputStream = variablesHandler.getBinaryVariablesHandler()
							.getBinaryVariableContent(binaryVariable);
					fileName = binaryVariable.getFileName();
					
					
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					byte buffer[] = new byte[1024];
					int noRead = 0;
					noRead = inputStream.read(buffer, 0, 1024);
					//Write out the stream to the file
					while (noRead != -1) {
						baos.write(buffer, 0, noRead);
						noRead = inputStream.read(buffer, 0, 1024);
					}
					rawPdfFile = baos.toByteArray();
					
					session.setAttribute(AscertiaConstants.PARAM_VARIABLE_HASH, variableHash);
					session.setAttribute(AscertiaConstants.PARAM_TASK_ID, taskInstanceId);
				//if document url was passed
				} else {

					URL url = new URL(documentURL);
					inputStream = url.openStream();

					fileName = url.getFile();
					while (fileName.indexOf("/") != -1) {
						fileName = fileName
								.substring(fileName.indexOf("/") + 1);
					}
					rawPdfFile = new byte[inputStream.available()];
					inputStream.read(rawPdfFile);
				}
				
				

				// Getting empty signature field
				byte[] pdfFileWithEmptySignature = null;

				EmptySignatureFieldRequest emptySigFieldRequest = new EmptySignatureFieldRequest(
						"samples_test_client", "adss:signing:profile:005",
						rawPdfFile);
				emptySigFieldRequest.overrideProfileAttribute(
						SigningRequest.SIGNING_REASON, "Testing");
				logger.log(Level.INFO,"A request has been sent to create blank signature(s) on the PDF. Waiting for response...");

				String EMPTY_SIGNATURE_URL = ADSS_URL + "esi";

				/* Sending the above constructed request to the ADSS server */
				EmptySignatureFieldResponse emptySigFieldResponse = (EmptySignatureFieldResponse) emptySigFieldRequest
						.send(EMPTY_SIGNATURE_URL);

				System.out.println("EMPTY_SIGNATURE_URI: "
						+ EMPTY_SIGNATURE_URL);

				if (emptySigFieldResponse.isResponseSuccessfull()) {
					pdfFileWithEmptySignature = emptySigFieldResponse
							.getSignedDocument();
					isResponseSuccessfull = true;
					logger.log(Level.INFO,"Empty signature request has been processed successfully.");
				} else {
					isResponseSuccessfull = false;
					System.out.println(emptySigFieldResponse.getErrorMessage());
				}

				if (isResponseSuccessfull) {
					isResponseSuccessfull = false;
					

					/* Constructing request for document hashing */

					DocumentHashingRequest documentHashingRequest = new DocumentHashingRequest(
							ORIGINATOR_ID, USER_PROFILE_ID,
							pdfFileWithEmptySignature, Base64
									.decode(certificate));

					/*documentHashingRequest.overrideProfileAttribute(
							SigningRequest.SIGNING_REASON, "asdasd");

					documentHashingRequest.overrideProfileAttribute(
							SigningRequest.SIGNING_LOCATION, "location");

					documentHashingRequest.overrideProfileAttribute(
							SigningRequest.CONTACT_INFO, "asd");*/

					/* Sending request to the ADSS server */

					DocumentHashingResponse documentHashingResponse = (DocumentHashingResponse) documentHashingRequest
							.send(HASHING_URL);

					if (documentHashingResponse.isResponseSuccessfull()) {

						isResponseSuccessfull = true;

						soapResponseBytes = documentHashingResponse
								.getDocumentHash();
						session.setAttribute("FileName", fileName);
						session.setAttribute("DocumentId",

						documentHashingResponse.getDocumentId());
						logger.log(Level.INFO,"Document hashing was successfull");

					} else {
						isResponseSuccessfull = false;
						errorMessage = documentHashingResponse
								.getErrorMessage();

					}
				}

			}

			/*
			 * Web application has been called second time, for Signature
			 * Assembly
			 */

			else {
				
				logger.log(Level.INFO,"Reading signature bytes");

				session = request.getSession(false);

				/*
				 * Input stream that reads the PKCS#7 bytes, that has been
				 * calculated by GoSign applet
				 */

				InputStream obj_inputStream = request.getInputStream();
				ByteArrayOutputStream obj_bos = new ByteArrayOutputStream();
				byte[] byte_buffer = new byte[128];
				int i_read = 0;
				while ((i_read = obj_inputStream.read(byte_buffer)) > 0) {
					obj_bos.write(byte_buffer, 0, i_read);

				}

				/* PKCS#7 bytes */

				b_pkcs7 = obj_bos.toByteArray();
				String str_docId = (String) session.getAttribute("DocumentId");

				String str_signedDocPath = /* str_path + */str_docId
						+ session.getAttribute("FileName");

				/* Constructing request for signature assembly */

				SignatureAssemblyRequest signatureAssemblyRequest = new SignatureAssemblyRequest(
						ORIGINATOR_ID, b_pkcs7, (String) session
								.getAttribute("DocumentId"));

				/* Sending request to the ADSS server */

				SignatureAssemblyResponse signatureAssemblyResponse = (SignatureAssemblyResponse) signatureAssemblyRequest
						.send(ASSEMBLY_URL);

				/* Setting dummy bytes */

				soapResponseBytes = new byte[] { '1', '2', '3' };

				if (signatureAssemblyResponse.isResponseSuccessfull()) {
					
					
					//writing to disk
//					signatureAssemblyResponse
//							.writeSignedPDFTo(str_signedDocPath);
					isResponseSuccessfull = true;
					signedDocument = signatureAssemblyResponse
							.getSignedDocument();
					
					//writeToSlide(signedDocument, str_signedDocPath);
					
					FacesContext fctx = WFUtil.createFacesContext(request.getSession().getServletContext(), request, response);
					IWContext iwc = IWContext.getIWContext(fctx);
					
					Integer variableHash = (Integer)session.getAttribute(AscertiaConstants.PARAM_VARIABLE_HASH);
					Long taskInstanceId = (Long)session.getAttribute(AscertiaConstants.PARAM_TASK_ID);

					VariablesHandler variablesHandler = getVariablesHandler(iwc
						.getServletContext());

					BinaryVariable binaryVariable = getBinVar(variablesHandler,
						taskInstanceId, variableHash);
					
					saveSignedPDFAttachment(iwc, binaryVariable, taskInstanceId, variableHash, signedDocument);
					
					logger.log(Level.INFO,"Documend successfully signed");
					
					
				} else {

					errorMessage = signatureAssemblyResponse.getErrorMessage();

				}

			}

			/* Sending the response back to the GoSign applet */
			
			if (isResponseSuccessfull) {

				/* Setting response headers */

				response.setStatus(HttpServletResponse.SC_OK);
				response.setContentType(CONTENT_TYPE);
				response.setContentLength(soapResponseBytes.length);

				/* Writing message on the response output stream */

				OutputStream outputStream = response.getOutputStream();

				outputStream.write(soapResponseBytes, 0,
						soapResponseBytes.length);
				
				
				
				outputStream.flush();
				outputStream.close();

			} else {

				logger.log(Level.INFO,"Response Failed...");

				if (isFirstTime) {

					logger.log(Level.SEVERE,"Hash Response Message : "
							+ errorMessage);

					response.sendError(
							HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
							errorMessage);

				} else {

					logger.log(Level.SEVERE,"Assembly Response Message : "
							+ errorMessage);
					response.sendError(
							HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
							errorMessage);

				}

			}
			
		} catch (Exception ex) {

			ex.printStackTrace();

			throw new ServletException("Unable to receive request : "
					+ ex.getMessage());

		}

	}

	protected boolean writeToSlide(byte[] documentToWrite, String documentName) {
		// Checking result of rendering process
		if (documentToWrite == null) {
			return false;
		}
		String uploadPath = "/files/cms/xform/pdf/signed/";
		// Checking file name and upload path
		if (!documentName.toLowerCase().endsWith(".pdf")) {
			documentName += ".pdf";
		}
		if (!uploadPath.startsWith(CoreConstants.SLASH)) {
			uploadPath = CoreConstants.SLASH + uploadPath;
		}
		if (!uploadPath.endsWith(CoreConstants.SLASH)) {
			uploadPath = uploadPath + CoreConstants.SLASH;
		}

		// Uploading PDF
		InputStream is = null;
		try {
			is = new ByteArrayInputStream(documentToWrite);

			return ((IWSlideService) IBOLookup.getServiceInstance(IWContext
					.getCurrentInstance(), IWSlideService.class))
					.uploadFileAndCreateFoldersFromStringAsRoot(
							/* BPMConstants.SIGNED_PDF_OF_XFORMS_PATH_IN_SLIDE */"/files/cms/xforms/pdf/signed/",
							documentName, is, MimeTypeUtil.MIME_TYPE_PDF_1,
							true);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			// is.close();
		}
		return false;

	}
	
	private void saveSignedPDFAttachment(IWContext iwc, BinaryVariable binaryVariable,long taskInstanceId, 
			Integer binaryVariableHash,byte[] signedPDF) throws Exception{
		
		
		TaskInstanceW taskInstance = getBpmFactory().getProcessManagerByTaskInstanceId(taskInstanceId)
				.getTaskInstance(taskInstanceId);
		

		String fileName = binaryVariable.getFileName().replace(".pdf", "_signed.pdf");
		
		InputStream inputStream = new ByteArrayInputStream(signedPDF);
	
		Variable variable = new Variable("signed_pdf_document_task",VariableDataType.FILE);
		
		try {
			String description = iwc.getIWMainApplication().getBundle("com.idega.ascertia").
			getResourceBundle(iwc).getLocalizedString("signed", "Signed")+ " " + binaryVariable.getDescription();
						
			BinaryVariable signedBinaryVariable = taskInstance.addAttachment(variable, fileName, description, inputStream);
			
			signedBinaryVariable.setSigned(true);
			signedBinaryVariable.store();
			
			binaryVariable.setHidden(true);
			binaryVariable.store();
			
			VariablesHandler variablesHandler = getVariablesHandler(iwc.getServletContext());
			
			inputStream = variablesHandler.getBinaryVariablesHandler().getBinaryVariableContent(binaryVariable);
			
			AscertiaData data = new AscertiaData();
			data.setDocumentName(fileName);
			data.setInputStream(inputStream);
			
			
			getAscertiaDataPull().push("tmpId", data);
			
		} catch(Exception e) {
			logger.log(Level.SEVERE, "Unable to set binary variable with signed document for task instance: " + taskInstanceId, e);
			throw new Exception(e);
			
		} 
		
	}

	private BinaryVariable getBinVar(VariablesHandler variablesHandler,
			long taskInstanceId, Integer binaryVariableHash) {

		List<BinaryVariable> variables = variablesHandler
				.resolveBinaryVariables(taskInstanceId);

		for (BinaryVariable binaryVariable : variables) {

			if (binaryVariable.getHash().equals(binaryVariableHash)) {

				return binaryVariable;
			}
		}

		return null;
	}

	private VariablesHandler getVariablesHandler(ServletContext ctx) {

		return ELUtil.getInstance().getBean("bpmVariablesHandler");
	}

}
