package org.webedded.cors;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet to proxy access where normally is need of CORS where has issues about
 * legacy browser without full implementation, like control of session.
 * 
 * @author Voiski<alannunesv@gmail.com>
 */
public class CorsProxyServlet extends HttpServlet {

	private static final long serialVersionUID = -3053743543344180905L;

	private static final String HEADER_FIELD_HOST = "Host";
	private static final String HEADER_FIELD_CONTENT_TYPE = "Content-Type";

	private static final String HEADER_FIELD_SET_COOKIE = "Set-Cookie";
	private static final String HEADER_FIELD_SET_COOKIE_VALUE_DELIMITER = ";";

	private static final String HEADER_FIELD_COOKIE_JSESSIONID = "JSESSIONID";

	private static final String PREFIX_FILE_PROTOCOL = "file://";
	private static final String PREFIX_CLASSPATH_PROTOCOL = "classpath://";

	// Init params from web.xml
	public static final String INIT_CONFIG_PATH = "org.webedded.cors.conf_path";
	public static final String INIT_HANDLER_CLASS = "org.webedded.cors.handler_class";

	// Params from resource config
	private static final String INIT_CONFIG_KEYSTORETYPE = "javax.net.ssl.keyStoreType";
	private static final String INIT_CONFIG_TRUSTSTORETYPE = "javax.net.ssl.trustStoreType";
	private static final String INIT_CONFIG_KEYSTORE = "javax.net.ssl.keyStore";
	private static final String INIT_CONFIG_TRUSTSTORE = "javax.net.ssl.trustStore";
	private static final String INIT_CONFIG_DEBUG = "javax.net.debug";
	private static final String INIT_CONFIG_KEYSTOREPASSWORD = "javax.net.ssl.keyStorePassword";
	private static final String INIT_CONFIG_TRUSTSTOREPASSWORD = "javax.net.ssl.trustStorePassword";

	private static final String HEADER_X_PROXY_LOCATION = "X-Proxy-Location";
	private static final String HEADER_X_REST_METHOD = "X-REST-Method";
	private static final String HEADER_TRANSFER_ENCODING = "Transfer-Encoding";

	public static final int DEFAULT_BUFFER_SIZE = 1024;

	private static final Map<String, ContextService> contextServicesMap = new HashMap<String, ContextService>();
	private static final Map<String, String> contextResoucesMap = new HashMap<String, String>();
	
	private static CorsProxyHandler handler;

	@Override
	public void init(final ServletConfig config) throws ServletException {
		super.init(config);

		// Get configuretion file for cors proxy
		final Properties externalConfiguration = new Properties();
		try {
			String confPath = config.getInitParameter(INIT_CONFIG_PATH);
			if (confPath == null) {
				confPath = "${jboss.server.home.dir}/conf/cors-proxy-conf.properties";
			}
			if(confPath.startsWith(PREFIX_CLASSPATH_PROTOCOL)){
				externalConfiguration
						.load(getClass().getResourceAsStream(
								confPath.substring(PREFIX_CLASSPATH_PROTOCOL
										.length())));
			}else if(new File(this.simpleElTransform(confPath)).exists()){
				externalConfiguration.load(new FileInputStream(this
						.simpleElTransform(confPath)));
			}
		} catch (final IOException e) {
			Logger.getLogger(this.getClass().getName())
					.warning(
							"Error loading CORS-Proxy configuration: "
									+ e.getMessage());
		}
		
		// Add servlet int parameters to config map, this will permite config
		// the same values inside the war but in external configuration will
		// override them.
		for (@SuppressWarnings("unchecked") final Enumeration<String> enumeration = config.getInitParameterNames(); enumeration.hasMoreElements();) {
			final String key = enumeration.nextElement();
			if(externalConfiguration.getProperty(key)!=null){
				externalConfiguration.setProperty(key, config.getInitParameter(key));
			}
		}
		
		initHandler(config);

		// Configure access to SSL
		setSystemPropertieIfExist(externalConfiguration, INIT_CONFIG_KEYSTORETYPE);
		setSystemPropertieIfExist(externalConfiguration, INIT_CONFIG_KEYSTORE);
		setSystemPropertieIfExist(externalConfiguration, INIT_CONFIG_KEYSTOREPASSWORD);

		setSystemPropertieIfExist(externalConfiguration, INIT_CONFIG_TRUSTSTORETYPE);
		setSystemPropertieIfExist(externalConfiguration, INIT_CONFIG_TRUSTSTORE);
		setSystemPropertieIfExist(externalConfiguration, INIT_CONFIG_TRUSTSTOREPASSWORD);

		setSystemPropertieIfExist(externalConfiguration, INIT_CONFIG_DEBUG);

		// Configure contexts of services/resources to concat operations
		for (final Enumeration<Object> keys = externalConfiguration.keys(); keys.hasMoreElements();) {
			final String singleKey = (String) keys.nextElement();
			if (singleKey.startsWith(ContextService.INIT_CONFIG_SUFIX_SERVICES)) {
				final String context = singleKey.substring(ContextService.INIT_CONFIG_SUFIX_SERVICES.length());
				final String url = this.simpleElTransform(externalConfiguration.getProperty(singleKey));
				contextServicesMap.put(context, ContextService.reuse(contextServicesMap.get(context),context, url));
			} else if (singleKey.startsWith(ContextService.INIT_CONFIG_SUFIX_FROM)) {
				final String context = singleKey.substring(ContextService.INIT_CONFIG_SUFIX_FROM.length());
				final String urlFrom = this.simpleElTransform(externalConfiguration.getProperty(
					singleKey
				));
				final String urlTo = this.simpleElTransform(externalConfiguration.getProperty(
					MessageFormat.format(ContextService.INIT_CONFIG_TO_MASK, context)
				));
				ContextService.getFromMap(contextServicesMap,context.substring(0,context.indexOf('.'))).putResource(urlFrom, urlTo);
			} else if (singleKey.startsWith(ContextService.INIT_CONFIG_COPY_HEADERS)){
				final String condition = this.simpleElTransform(externalConfiguration.getProperty(singleKey));
				if("true".equalsIgnoreCase(condition) || "1".equalsIgnoreCase(condition)){
					final String context = singleKey.substring(ContextService.INIT_CONFIG_COPY_HEADERS.length());
					ContextService.getFromMap(contextServicesMap,context).enableCopyHeaders();
				}
			}
		}
	}
	
	/**
	 * Configure handler, if not exist custom class use a single implementation.
	 */
	private void initHandler(final ServletConfig config)
			throws ServletException {
		if(config.getInitParameter(INIT_HANDLER_CLASS)!=null){
			try {
				@SuppressWarnings("rawtypes")
				final Class clazz = this.getClass().getClassLoader().loadClass(config.getInitParameter(INIT_HANDLER_CLASS));
				handler = (CorsProxyHandler) clazz.newInstance();
			} catch (final ClassNotFoundException e) {
				Logger.getLogger(CorsProxyServlet.class.getName()).log(
						Level.SEVERE, null, e);
				throw new ServletException("Error in configuration of handler for CORS Proxy!",e);
			} catch (InstantiationException e) {
				Logger.getLogger(CorsProxyServlet.class.getName()).log(
						Level.SEVERE, null, e);
				throw new ServletException("Error in configuration of handler for CORS Proxy!",e);
			} catch (IllegalAccessException e) {
				Logger.getLogger(CorsProxyServlet.class.getName()).log(
						Level.SEVERE, null, e);
				throw new ServletException("Error in configuration of handler for CORS Proxy!",e);
			}
		}else{
			handler = new CorsProxyHandler(){

				public void handleRequestProperty(final HttpURLConnection connection,
						HttpServletRequest request) {
					
				}

				public void handleResponseHeader(final HttpServletRequest request,
						HttpServletResponse response) {
					
				}

				public void initServlet(final CorsProxyServlet corsProxyServlet) {
					
				}

				public boolean handleResponseCode(final HttpServletRequest request,
						final HttpServletResponse response, final String proxyUrl,
						final int responseCode) {
					return true;
				}
				
			};
		}
		handler.initServlet(this);
	}

	@Override
	public void service(final HttpServletRequest request,
			final HttpServletResponse response) throws ServletException,
			IOException {
		final String requestUrl = request.getQueryString() == null ? request
				.getRequestURL().toString() : MessageFormat.format("{0}?{1}",
				request.getRequestURL().toString(), request.getQueryString());
		final String proxyUrl = this.buildUrl(requestUrl, request.getServletPath());

		if (proxyUrl == null) {
			throw new ServletException("No " + HEADER_X_PROXY_LOCATION
					+ " header present.");
		}

		try {
			if (proxyUrl.startsWith(PREFIX_FILE_PROTOCOL)) {
				reponseFromFile(request, response, proxyUrl);
			} else if (proxyUrl.startsWith(PREFIX_CLASSPATH_PROTOCOL)) {
				reponseFromClasspath(request, response, proxyUrl);
			} else {
				reponseFromConnection(request, response, proxyUrl);
			}

		} catch (final IOException ex) {
			Logger.getLogger(CorsProxyServlet.class.getName()).log(
					Level.SEVERE, null, ex);

			throw new ServletException(ex);
		}
	}

	/**
	 * Create the response for overrided resource ignoring proxy to origin
	 * service
	 */
	private void reponseFromFile(final HttpServletRequest request,
			final HttpServletResponse response, final String proxyUrl)
			throws IOException {
		response.setHeader(HEADER_FIELD_CONTENT_TYPE,
				request.getHeader(HEADER_FIELD_CONTENT_TYPE));
		handler.handleResponseHeader(request, response);
		this.copyStream(
				new FileInputStream(proxyUrl.substring(PREFIX_FILE_PROTOCOL
						.length())), response.getOutputStream());
	}

	/**
	 * Create the response for overrided resource inside classpath ignoring
	 * proxy to origin service
	 */
	private void reponseFromClasspath(final HttpServletRequest request,
			final HttpServletResponse response, final String proxyUrl)
			throws IOException {
		response.setHeader(HEADER_FIELD_CONTENT_TYPE,
				request.getHeader(HEADER_FIELD_CONTENT_TYPE));
		handler.handleResponseHeader(request, response);
		this.copyStream(
				getClass().getResourceAsStream(
						proxyUrl.substring(PREFIX_CLASSPATH_PROTOCOL.length())),
				response.getOutputStream());
	}

	/**
	 * Create the response for proxy connection of a service
	 */
	public void reponseFromConnection(final HttpServletRequest request,
			final HttpServletResponse response, final String proxyUrl)
			throws IOException {
		final String serviceContext = this.getUrlContext(request
				.getRequestURL().toString(), request.getServletPath());

		final StringBuffer bufferStream = new StringBuffer("");

		HttpURLConnection connection = createConnection(request, response,
				proxyUrl, bufferStream);

		response.setStatus(connection.getResponseCode());
		
		if(handler.handleResponseCode(request, response, proxyUrl, connection.getResponseCode())){
			// Pass back headers
			final Map<String, List<String>> responseHeaders = connection
					.getHeaderFields();
	
			final ContextService contextService = contextServicesMap.get(serviceContext);
			for (final Entry<String, List<String>> entry : responseHeaders
					.entrySet()) {
				if (entry.getKey() != null && !HEADER_TRANSFER_ENCODING.equalsIgnoreCase(entry.getKey())) {
					if (entry.getKey().equalsIgnoreCase(
							HEADER_FIELD_SET_COOKIE)) {
						String cookieValue = this.concatComma(entry.getValue());
						cookieValue = cookieValue.substring(cookieValue
								.indexOf("=") + 1, cookieValue
								.indexOf(HEADER_FIELD_SET_COOKIE_VALUE_DELIMITER));
						request.getSession().setAttribute(
								HEADER_FIELD_COOKIE_JSESSIONID + "-"
										+ serviceContext, cookieValue);
					}else if (contextService.isCopyHeaders() || entry.getKey().equalsIgnoreCase(HEADER_FIELD_CONTENT_TYPE)) {
						response.setHeader(entry.getKey(),
								this.concatComma(entry.getValue()));
					}
				}
			}
			
			handler.handleResponseHeader(request, response);
			try{
				this.copyStream(connection.getInputStream(), response.getOutputStream());
			}catch(final IOException ioe){
				if(ioe.getMessage().contains("Server returned HTTP")){
					this.copyStream(connection.getErrorStream(), response.getOutputStream());
				}else{
					throw ioe;
				}
			}
		}
	}

	/**
	 * Create the connection with parsed header and content body of initial
	 * request.
	 */
	private HttpURLConnection createConnection(
			final HttpServletRequest request,
			final HttpServletResponse response, final String proxyUrl,
			final StringBuffer bufferStream) throws IOException,
			MalformedURLException, ProtocolException {

		final HttpURLConnection connection = (HttpURLConnection) new URL(
				proxyUrl).openConnection();
		final String serviceContext = this.getUrlContext(request
				.getRequestURL().toString(), request.getServletPath());

		String method = request.getHeader(proxyUrl);
		if (method == null) {
			method = request.getMethod();
		}
		final boolean doOutput = method.equalsIgnoreCase("POST")
				|| method.equalsIgnoreCase("PUT");

		if (proxyUrl.toLowerCase().startsWith("https")) {
			((HttpsURLConnection) connection)
					.setSSLSocketFactory((SSLSocketFactory) SSLSocketFactory
							.getDefault());
		}

		connection.setDoOutput(doOutput);
		connection.setRequestMethod(method.toUpperCase());

		final String lastSessionID = (String) request.getSession()
				.getAttribute(
						HEADER_FIELD_COOKIE_JSESSIONID + "-" + serviceContext);
		// Duplicate headers
		for (@SuppressWarnings("rawtypes")
		final Enumeration enu = request.getHeaderNames(); enu.hasMoreElements();) {
			final String headerName = (String) enu.nextElement();

			if (headerName.equals(HEADER_X_REST_METHOD)
					|| headerName.equals(HEADER_X_PROXY_LOCATION)) {
				continue;
			}

			final String headerValue = request.getHeader(headerName);

			if (HEADER_FIELD_HOST.equalsIgnoreCase(headerName)) {
				connection.setRequestProperty(HEADER_FIELD_HOST, connection
						.getURL().getHost());
			} else if ("Cookie".equalsIgnoreCase(headerName)
					&& lastSessionID != null) {
				final String[] cookies = headerValue
						.split(HEADER_FIELD_SET_COOKIE_VALUE_DELIMITER);
				final StringBuilder cookiesForHeaderValue = new StringBuilder(
						headerValue.length());
				cookiesForHeaderValue.append(HEADER_FIELD_COOKIE_JSESSIONID)
						.append("=").append(lastSessionID)
						.append(HEADER_FIELD_SET_COOKIE_VALUE_DELIMITER);
				for (final String cookie : cookies) {
					if (!cookie.startsWith(HEADER_FIELD_COOKIE_JSESSIONID)) {
						cookiesForHeaderValue.append(cookie).append(
								HEADER_FIELD_SET_COOKIE_VALUE_DELIMITER);
					}
				}
				connection.setRequestProperty(headerName,
						cookiesForHeaderValue.toString());
			} else {
				connection.setRequestProperty(headerName, headerValue);
			}
		}
		
		handler.handleRequestProperty(connection, request);

		if (doOutput) {
			this.copyStream(request.getInputStream(),
					connection.getOutputStream());
		}

		return connection;
	}

	/**
	 * @return url from context service or path to resource if has override
	 *         configured
	 */
	private String buildUrl(final String requestUrl, final String servletPath) {
		final String urlContent = requestUrl.split(servletPath)[1];
		final String serviceContext = urlContent.substring(1,urlContent.indexOf('/', 1));
		
		return contextServicesMap.get(serviceContext).getOriginUrl(urlContent);
	}

	/**
	 * @return context of map for parser of url, this isnt the context of url.
	 */
	private String getUrlContext(final String requestUrl,
			final String servletPath) {
		final String urlContent = requestUrl.split(servletPath)[1];
		final String serviceContext = urlContent.substring(1,
				urlContent.indexOf('/', 1));

		return serviceContext;
	}

	/**
	 * Copies the data from an InputStream object to an OutputStream object.
	 * 
	 * @param sourceStream
	 *            The input stream to be read.
	 * @param destinationStream
	 *            The output stream to be written to.
	 * @exception IOException
	 *                from java.io calls.
	 */
	private void copyStream(final InputStream sourceStream,
			final OutputStream destinationStream) throws IOException {
		int bytesRead = 0;
		final byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];

		while (bytesRead >= 0) {
			bytesRead = sourceStream.read(buffer, 0, buffer.length);

			if (bytesRead > 0) {
				destinationStream.write(buffer, 0, bytesRead);
			}
		}
	}

	private String simpleElTransform(final String elValue) {
		final String result;

		if (elValue==null || !elValue.contains("${")) {
			result = elValue;
		} else {
			final StringBuilder resultBuilder = new StringBuilder(
					elValue.length());
			final String[] parties = elValue.split("[\\$}]");
			for (final String single : parties) {
				if (single.length() > 0 && single.charAt(0) == '{') {
					if (System.getProperty(single.substring(1)) != null) {
						resultBuilder.append(System.getProperty(single
								.substring(1)));
					}
				} else {
					resultBuilder.append(single);
				}
			}
			result = resultBuilder.toString();
		}

		return result;
	}

	private String concatComma(final List<String> strings) {
		final Iterator<String> it = strings.iterator();
		StringBuilder sb = new StringBuilder(it.next());

		while (it.hasNext()) {
			sb = sb.append(",").append(it.next());
		}

		return sb.toString();
	}

	private void setSystemPropertieIfExist(final Properties externalConfiguration, final String key) {
		final String value=externalConfiguration.getProperty(key);
		if(value!=null){
			System.getProperties().setProperty(key, this.simpleElTransform(value));
		}
	}

	/**
	 * You can use this to add new services with handler if you dont want to use
	 * external config.
	 * 
	 * @return map with services
	 */
	public Map<String, ContextService> getContextServicesMap() {
		return contextServicesMap;
	}

	/**
	 * You can use this to add new resources with handler if you dont want to
	 * use external config.
	 * 
	 * @return map with resources
	 */
	public Map<String, String> getContextResoucesMap() {
		return contextResoucesMap;
	}

}