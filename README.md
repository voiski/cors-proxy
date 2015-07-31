CORS-Proxy
=================
[![Build Status](https://travis-ci.org/voiski/cors-proxy.svg?branch=master)](https://travis-ci.org/voiski/cors-proxy) [![Coverage Status](https://coveralls.io/repos/voiski/cors-proxy/badge.svg?branch=master&service=github)](https://coveralls.io/github/voiski/cors-proxy?branch=master)

Servlet to proxy requests involving Cross-Origin Resource Sharing (CORS) due to cross-domain problem. Some browsers do not implemented completely the CORS creating a problem of access to resources, events that require sending cookies to identification of the session. This servlet will make a proxy for the true service.

Instalation
--------------------
The installation consist in export the authentication key target address certificate, configure your access data in the servlet configuration property, add the jar to the classpath and configure it in the application web.xml.

####Classpath
Simply put the jar in classpath of your web application or use maven:

```xml
	<dependency>
		<groupId>org.webedded.cors</groupId>
		<artifactId>cors-proxy</artifactId>
		<version>1.0.4</version>
	</dependency>
```


> Now in central maven starting with version 1.0.4, see tags for old releases.

####Generate KeyStore/TrustStore
Server certificates signed with well known public Certificate Authority dont need this config.

`TODO Document how`

####Config Properties
You can configure in three ways:

- By external config file with key=value, the path to this file will be used in Web.xml.
- By init parameter of servlet in in web.xml
- By init method in handler class.

The keys to configure:

- **javax.net.ssl.keyStore** path for KeyStore, any system property will be replaced like ${jboss.server.home.dir}.
- **javax.net.ssl.keyStorePassword** password to access keyStore
- **javax.net.ssl.keyStoreType** type of encrypt, ex: PKCS12
- **javax.net.ssl.trustStore** path for TrustStore, any system property will be replaced like ${jboss.server.home.dir}.
- **javax.net.ssl.trustStorePassword** password to access trustStore
- **javax.net.ssl.trustStoreType** type of encrypt, ex: jks
- **javax.net.debug** Inform `ssl` to debug the ssl connection interactions
- **server.<mapped_origin_context>** original url to be decorated, you can configure multiple services like:

> server.cont1=https://sample.com/programX

> server.cont2=https://sample2.org/programY

- **resource.from.<some_index>** if you desire to override one resource, you can refer to any other but this will reuse the parameter `content-type` of request header in the response header. This configuration need pair config by setting the **resource.to**. In this first you need to declare the url with the `/<mapped_origin_context>/<second_part_original_url>`.
- **resource.to.<some_index>**  In this second config, you refer to new service or file. File needs to specify protocol `file://`.

> resource.from.cont1-index=/cont1/index.html

> resource.to.cont1-index=file:///path/to/file.html

####Config Web.xml
```xml
	<servlet>
		<servlet-name>pxcors</servlet-name>
		<servlet-class>org.webedded.cors.CorsProxyServlet</servlet-class>
		<init-param>
			<param-name>org.webedded.cors.conf_path</param-name>
			<param-value>${jboss.server.home.dir}/conf/cors-proxy-conf.properties</param-value>
		</init-param>
	</servlet>
	<servlet-mapping>
		<servlet-name>pxcors</servlet-name>
		<url-pattern>/pxcors/*</url-pattern>
	</servlet-mapping>
```


How to use
--------------------
Just use as follows in links/requests:

> http://localhost/context/${mapped_origin_context}/${second_part_original_url}

Sample:

> http://localhost/appExemplo/cont1/#/sample/doSomething
>
> http://localhost/appExemplo/cont2/page.do?dispatchMethod=ACTION_SOMETHING
