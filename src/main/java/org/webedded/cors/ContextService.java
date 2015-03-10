package org.webedded.cors;

import java.util.HashMap;
import java.util.Map;

/**
 * Wrap to Context Service scope with the necessary values to configuration the
 * proxy.
 * 
 * @author Voiski<alannunesv@gmail.com>
 */
public class ContextService {

	private String name;
	private String url;
	private Boolean copyHeaders = Boolean.FALSE;
	private static final Map<String, String> contextResoucesMap = new HashMap<String, String>();
	private static final String PREFIX_FILE_PROTOCOL = "file://";
	public static final String INIT_CONFIG_SUFIX_SERVICES = "server.";
	public static final String INIT_CONFIG_SUFIX_FROM = "resource.from.";
	public static final String INIT_CONFIG_TO_MASK = "resource.to.{0}";
	public static final String INIT_CONFIG_COPY_HEADERS = "headers.";

	/**
	 * @see #c()
	 */
	protected ContextService() {
	}

	/**
	 * @return new context service wrap
	 */
	public static ContextService c(final String theName, final String theUrl) {
		final ContextService contextService = new ContextService();
		contextService.name = theName;
		contextService.url = theUrl;
		return contextService;
	}

	/**
	 * @deprecated This is temporary
	 * @return new context service wrap
	 */
	public static ContextService reuse(final ContextService service, final String theName, final String theUrl) {
		final ContextService contextService = service==null?new ContextService():service;
		contextService.name = theName;
		contextService.url = theUrl;
		return contextService;
	}

	/**
	 * @deprecated This is temporary
	 * @return context service wrap, create if not exist
	 */
	public static ContextService getFromMap(final Map<String, ContextService> contextServicesMap, final String context) {
		if(!contextServicesMap.containsKey(context)){
			contextServicesMap.put(context, c(context,""));
		}
		return contextServicesMap.get(context);
	}

	/**
	 * @return name of context
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return origin url to be proxied
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * @return true if the behavior of copy all headers was enable
	 */
	public Boolean isCopyHeaders() {
		return copyHeaders;
	}

	/**
	 * Enable the behavior of copy all headers from origin response to proxy
	 * response
	 */
	public void enableCopyHeaders() {
		this.copyHeaders = Boolean.TRUE;
	}

	/**
	 * @param urlFrom
	 *            reference to url
	 * @return the path destination
	 */
	public String getResource(final String urlFrom) {
		return contextResoucesMap.get(urlFrom);
	}

	/**
	 * @param urlFrom
	 *            reference to url
	 * @return the path destination
	 */
	public void putResource(final String urlFrom, final String urlTo) {
		contextResoucesMap.put(urlFrom, urlTo);
	}

	/**
	 * url origin or the path to file if exist in resource override feature
	 * 
	 * @param urlContent
	 *            url base
	 * @return url origin
	 */
	public String getOriginUrl(final String urlContent) {
		String originDestination = null;
		for (final String single : contextResoucesMap.keySet()) {
			if (urlContent.startsWith(single)) {
				originDestination = contextResoucesMap.get(single);
				if (!originDestination.startsWith(PREFIX_FILE_PROTOCOL)) {
					originDestination = originDestination.concat(urlContent.substring(single.length()));
				}
				break;
			}
		}
		
		if (originDestination== null) {
			originDestination = url.concat(urlContent.substring(1 + name.length()));
		}
		return originDestination;
	}

}
