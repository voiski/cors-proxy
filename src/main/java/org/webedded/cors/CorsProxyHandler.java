package org.webedded.cors;

import java.io.IOException;
import java.net.HttpURLConnection;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handler for exceptions and other controls extensions.
 * 
 * @author Voiski<alannunesv@gmail.com>
 */
public interface CorsProxyHandler {

	/**
	 * Customize parameters for request of service.
	 * 
	 * @param connection proxy connection
	 * @param request current request
	 */
	void handleRequestProperty(HttpURLConnection connection, HttpServletRequest request) throws IOException;

	/**
	 * Customize parameters for head of response
	 * 
	 * @param request current request
	 * @param response current response
	 */
	void handleResponseHeader(HttpServletRequest request,HttpServletResponse response) throws IOException;

	/**
	 * Control the response by code response from original service.
	 * 
	 * @param request current request
	 * @param response current response
	 * @param proxyUrl url of original service 
	 * @param responseCode response code of original service request.
	 * @return true if this can continue with normal response, false otherwise.
	 */
	boolean handleResponseCode(HttpServletRequest request,HttpServletResponse response, String proxyUrl, int responseCode) throws IOException;

	/**
	 * Set servlet instance to permite interactions with some values like maps.
	 * 
	 * @param corsProxyServlet current servlet
	 */
	void setServlet(CorsProxyServlet corsProxyServlet);

}
