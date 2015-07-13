package org.webedded.cors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Vector;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Test servlet
 * 
 * @author Voiski<alannunesv@gmail.com>
 */
public class CorsProxyServletTest extends Mockito {
	
	private static CorsProxyServlet servlet;
	
	@BeforeClass
	public static void setupClass() throws ServletException, IOException{
		//Workaoud to work in eclipse, the plugin maven is instable
		if(System.getProperty("project.build.directory")==null){
			System.setProperty("project.build.directory",System.getProperty("user.dir")+"/target");
		}
		
		HttpServlet config = mock(HttpServlet.class);
		when(config.getInitParameter(CorsProxyServlet.INIT_CONFIG_PATH)).thenReturn(System.getProperty("project.build.directory")+"/test-classes/cors-proxy-conf.properties");
		when(config.getInitParameterNames()).thenReturn(new Vector<String>().elements());
		
		servlet = new CorsProxyServlet();
		servlet.init(config);
		
	}

	@Test
	public void testServiceGetTextHttp() throws IOException, ServletException {
		HttpServletRequest request = createRequest("http://cors.com.br/context/pxcors/rgv/cors-proxy/master/README.md");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        HttpServletResponse response = createResponse(output);
        
        servlet.service(request, response);
        
        Assert.assertTrue("Need to return the content of file!",output.toString().length()>0);
	}

	@Test
	public void testServiceGetTextHttps() throws IOException, ServletException {
		HttpServletRequest request = createRequest("https://cors.com.br/context/pxcors/regbrtec/epp-formulario.txt");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        HttpServletResponse response = createResponse(output);
        
        servlet.service(request, response);
        
        Assert.assertTrue("Need to return the content of file!",output.toString().length()>0);
	}

	@Test
	public void testServiceGetImageHttps() throws IOException, ServletException {
		HttpServletRequest request = createRequest("https://cors.com.br/context/pxcors/grav/u/1316207?v=3&s=40");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        HttpServletResponse response = createResponse(output);
        
        servlet.service(request, response);
        
        Assert.assertTrue("Need to return the content of file!",output.toString().length()>0);
	}

	@Test
	public void testServiceResourceOverride() throws IOException, ServletException {
		HttpServletRequest request = createRequest("https://cors.com.br/context/pxcors/pol/index-col-full.html");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        HttpServletResponse response = createResponse(output);
        
        servlet.service(request, response);
        
        Assert.assertTrue("Need to return the content of file!",output.toString().length()>0);
        Assert.assertTrue("Has test text!",output.toString().contains("Resource override test"));
	}

	/**
	 * @return response sample
	 */
	private HttpServletResponse createResponse(final ByteArrayOutputStream output)
			throws IOException {
		final HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getOutputStream()).thenReturn(new StubServletOutputStream(output));
        when(response.getWriter()).thenReturn(new PrintWriter(output));
		return response;
	}

	/**
	 * @param url 
	 * @return request sample
	 */
	private HttpServletRequest createRequest(final String url) {
		final HttpSession session = mock(HttpSession.class);
		
		final Vector<String> header = new Vector<String>();
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getServletPath()).thenReturn("/pxcors");
        when(request.getHeaderNames()).thenReturn(header.elements());
        when(request.getRequestURL()).thenReturn(new StringBuffer(url));
        when(request.getSession()).thenReturn(session);
		return request;
	}

}
