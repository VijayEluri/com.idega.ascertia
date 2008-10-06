package com.idega.ascertia;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;

import com.idega.io.DownloadWriter;
import com.idega.presentation.IWContext;
import com.idega.util.FileUtil;
import com.idega.util.expression.ELUtil;

public class AscertiaPDFWrinter extends DownloadWriter{
	
	public static final String PARAM_CONVERSATION_ID = "conversation_id";

	private static final Logger logger = Logger.getLogger(AscertiaPDFWrinter.class.getName());
	
	@Autowired
	private AscertiaDataPull ascertiaDataPull;
	
	private InputStream inputStream;
		
	public AscertiaDataPull getAscertiaDataPull() {
		return ascertiaDataPull;
	}

	public void setAscertiaDataPull(AscertiaDataPull ascertiaDataPull) {
		this.ascertiaDataPull = ascertiaDataPull;
	}

	@Override
	public void init(HttpServletRequest req, IWContext iwc) {
		ELUtil.getInstance().autowire(this);
		String conversationId = iwc.getParameter(PARAM_CONVERSATION_ID);
		AscertiaData ascertiaData = getAscertiaDataPull().pop(conversationId);
		inputStream = ascertiaData.getInputStream();
		
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte buffer[] = new byte[1024];
		int noRead = 0;
		try {
			noRead = inputStream.read(buffer, 0, 1024);
			while (noRead != -1) {
				baos.write(buffer, 0, noRead);
				noRead = inputStream.read(buffer, 0, 1024);
			}
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Unable to read from input stream",e);
			inputStream = null;
			return;
		}
		ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());	
		inputStream = bais;
		
		setAsDownload(iwc, ascertiaData.getDocumentName(), bais.available());
	}
	
	@Override
	public void writeTo(OutputStream streamOut) throws IOException {
		if (inputStream == null) {
			logger.log(Level.SEVERE, "Unable to get input stream");
			return;
		}
		
		FileUtil.streamToOutputStream(inputStream, streamOut);
		
		streamOut.flush();
		streamOut.close();
	}
	
}
