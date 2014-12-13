package org.webedded.cors;

import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.ServletOutputStream;

/**
 * Mocked outputStream of servlet response
 * 
 * @author Voiski<alannunesv@gmail.com>
 */
public class StubServletOutputStream extends ServletOutputStream {
	private OutputStream outStream;

	public StubServletOutputStream(OutputStream outStream) {
		this.outStream = outStream;
	}

	@Override
	public void write(int b) throws IOException {
		outStream.write(b);
	}

}
