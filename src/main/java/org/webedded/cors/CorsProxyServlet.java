package org.webedded.cors;

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

	// Init params from web.xml
	public static final String INIT_CONFIG_PATH = "org.webedded.cors.conf_path";

	// Params from resource config
	private static final String INIT_CONFIG_KEYSTORETYPE = "javax.net.ssl.keyStoreType";
	private static final String INIT_CONFIG_TRUSTSTORETYPE = "javax.net.ssl.trustStoreType";
	private static final String INIT_CONFIG_KEYSTORE = "javax.net.ssl.keyStore";
	private static final String INIT_CONFIG_TRUSTSTORE = "javax.net.ssl.trustStore";
	private static final String INIT_CONFIG_DEBUG = "javax.net.debug";
	private static final String INIT_CONFIG_KEYSTOREPASSWORD = "javax.net.ssl.keyStorePassword";
	private static final String INIT_CONFIG_TRUSTSTOREPASSWORD = "javax.net.ssl.trustStorePassword";

	private static final String INIT_CONFIG_SUFIX_SERVICES = "server.";
	private static final String INIT_CONFIG_SUFIX_FROM = "resource.from.";
	private static final String INIT_CONFIG_TO_MASK = "resource.to.{0}";

	private static final String X_PROXY_LOCATION_HEADER = "X-Proxy-Location";
	private static final String X_REST_METHOD_HEADER = "X-REST-Method";

	public static final int DEFAULT_BUFFER_SIZE = 1024;

	private static final Map<String, String> contextServicesMap = new HashMap<String, String>();
	private static final Map<String, String> contextResoucesMap = new HashMap<String, String>();

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
			externalConfiguration.load(new FileInputStream(this
					.simpleElTransform(confPath)));
		} catch (final IOException e) {
			Logger.getLogger(this.getClass().getName())
					.warning(
							"Error loading CORS-Proxy configuration: "
									+ e.getMessage());
		}

		// Configure access to SSL
		setSystemPropertieIfExist(externalConfiguration, INIT_CONFIG_KEYSTORETYPE);
		setSystemPropertieIfExist(externalConfiguration, INIT_CONFIG_KEYSTORE);
		setSystemPropertieIfExist(externalConfiguration, INIT_CONFIG_KEYSTOREPASSWORD);

		setSystemPropertieIfExist(externalConfiguration, INIT_CONFIG_TRUSTSTORETYPE);
		setSystemPropertieIfExist(externalConfiguration, INIT_CONFIG_TRUSTSTORE);
		setSystemPropertieIfExist(externalConfiguration, INIT_CONFIG_TRUSTSTOREPASSWORD);

		setSystemPropertieIfExist(externalConfiguration, INIT_CONFIG_DEBUG);

		// Configure contexts of services/resources to concat operations
		for (final Enumeration<Object> keys = externalConfiguration.keys(); keys
				.hasMoreElements();) {
			final String singleKey = (String) keys.nextElement();
			if (singleKey.startsWith(INIT_CONFIG_SUFIX_SERVICES)) {
				final String context = singleKey
						.substring(INIT_CONFIG_SUFIX_SERVICES.length());
				final String url = this.simpleElTransform(externalConfiguration
						.getProperty(singleKey));
				contextServicesMap.put(context, url);
			} else if (singleKey.startsWith(INIT_CONFIG_SUFIX_FROM)) {
				final String context = singleKey
						.substring(INIT_CONFIG_SUFIX_FROM.length());
				final String urlFrom = this
						.simpleElTransform(externalConfiguration
								.getProperty(singleKey));
				final String urlTo = this
						.simpleElTransform(externalConfiguration
								.getProperty(MessageFormat.format(
										INIT_CONFIG_TO_MASK, context)));
				contextResoucesMap.put(urlFrom, urlTo);
			}
		}
	}

	@Override
	public void service(final HttpServletRequest request,
			final HttpServletResponse response) throws ServletException,
			IOException {
		final String proxyUrl = this.buildUrl(request.getRequestURL()
				.toString(), request.getServletPath());

		if (proxyUrl == null) {
			throw new ServletException("No " + X_PROXY_LOCATION_HEADER
					+ " header present.");
		}

		try {
			if (proxyUrl.startsWith(PREFIX_FILE_PROTOCOL)) {
				reponseFromFile(request, response, proxyUrl);
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
	 * Create the response for overrided resource ignorindo proxy to origin
	 * service
	 */
	private void reponseFromFile(final HttpServletRequest request,
			final HttpServletResponse response, final String proxyUrl)
			throws IOException {
		response.setHeader(HEADER_FIELD_CONTENT_TYPE,
				request.getHeader(HEADER_FIELD_CONTENT_TYPE));
		this.copyStream(
				new FileInputStream(proxyUrl.substring(PREFIX_FILE_PROTOCOL
						.length())), response.getOutputStream());
	}

	/**
	 * Create the response for proxy connection of a service
	 */
	private void reponseFromConnection(final HttpServletRequest request,
			final HttpServletResponse response, final String proxyUrl)
			throws IOException {
		final String serviceContext = this.getUrlContext(request
				.getRequestURL().toString(), request.getServletPath());

		final StringBuffer bufferStream = new StringBuffer("");

		HttpURLConnection connection = createConnection(request, response,
				proxyUrl, bufferStream);

		response.setStatus(connection.getResponseCode());

		// Pass back headers
		final Map<String, List<String>> responseHeaders = connection
				.getHeaderFields();

		for (final Entry<String, List<String>> entry : responseHeaders
				.entrySet()) {
			if (entry.getKey() != null) {
				if (entry.getKey().equalsIgnoreCase(HEADER_FIELD_CONTENT_TYPE)) {
					response.setHeader(entry.getKey(),
							this.concatComma(entry.getValue()));
				} else if (entry.getKey().equalsIgnoreCase(
						HEADER_FIELD_SET_COOKIE)) {
					String cookieValue = this.concatComma(entry.getValue());
					cookieValue = cookieValue.substring(cookieValue
							.indexOf("=") + 1, cookieValue
							.indexOf(HEADER_FIELD_SET_COOKIE_VALUE_DELIMITER));
					request.getSession().setAttribute(
							HEADER_FIELD_COOKIE_JSESSIONID + "-"
									+ serviceContext, cookieValue);
				}
			}
		}

		this.copyStream(connection.getInputStream(), response.getOutputStream());
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

			if (headerName.equals(X_REST_METHOD_HEADER)
					|| headerName.equals(X_PROXY_LOCATION_HEADER)) {
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
		final String serviceContext = urlContent.substring(1,
				urlContent.indexOf('/', 1));

		String url = this.getPathResourceIfExist(urlContent);
		if (url == null) {
			url = contextServicesMap.get(serviceContext).concat(
					urlContent.substring(1 + serviceContext.length()));
		}

		return url;
	}

	private String getPathResourceIfExist(final String urlContent) {
		String path = null;
		for (final String single : contextResoucesMap.keySet()) {
			if (urlContent.startsWith(single)) {
				path = contextResoucesMap.get(single);
				if (!path.startsWith(PREFIX_FILE_PROTOCOL)) {
					path = path.concat(urlContent.substring(single.length()));
				}
				break;
			}
		}
		return path;
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
}