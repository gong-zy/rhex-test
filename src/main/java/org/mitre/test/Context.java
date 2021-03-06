package org.mitre.test;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang.StringUtils;
import org.apache.http.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jdom.input.SAXBuilder;
import org.mitre.test.impl.TextReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ErrorHandler;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * Application context handles configuration and general house keeping.
 * Abstracts the tests from most server implementation details including
 * authentication and security handling.
 *
 * @author Jason Mathews, MITRE Corp.
 * Date: 2/20/12 10:55 AM
 */
public class Context {

	private static final Logger log = LoggerFactory.getLogger(Context.class);

    public static final String DEFAULT_USER = "defaultUser";

    private SAXBuilder builder, validatingBuilder;

    private Reporter reporter;

	/** Validation feature id */
	protected static final String VALIDATION_FEATURE =
		"http://xml.org/sax/features/validation";

	/** Schema validation feature id */
	protected static final String SCHEMA_VALIDATION_FEATURE =
		"http://apache.org/xml/features/validation/schema";

	/** Schema full checking feature id */
	protected static final String SCHEMA_FULL_CHECKING_FEATURE =
		"http://apache.org/xml/features/validation/schema-full-checking";

	protected static final String LOAD_DTD_GRAMMAR =
		"http://apache.org/xml/features/nonvalidating/load-dtd-grammar";  // [TRUE]

	protected static final String LOAD_EXTERNAL_DTD =
		"http://apache.org/xml/features/nonvalidating/load-external-dtd"; // [TRUE]

	protected static final String CONTINUE_AFTER_FATAL_FEATURE =
		"http://apache.org/xml/features/continue-after-fatal-error"; // [FALSE]

	@NonNull
	private URI baseURL;

	private HttpHost proxy;

	// security info
	// root.xml contents ?

	private XMLConfiguration config;

	/**
	 * server specific implementation of HttpRequestChecker to pre-test HTTP requests
	 * such as handling authentication
	 */
	private HttpRequestChecker httpRequestChecker;

	private String baseUrlString; // cached copy of baseURL.toASCIIString()

    private final Map<String, UserInfo> userMap = new HashMap<String, UserInfo>();
    private String currentUser;

    @NonNull
	public URI getBaseURL() {
		return baseURL;
	}

	@NonNull
	public URI getBaseURL(String relativePath) throws URISyntaxException {
		if (StringUtils.isBlank(relativePath)) {
			return baseURL;
		}
		String uri = baseUrlString; // baseURL.toASCIIString();
		if (relativePath.startsWith("/")) {
			// relative paths are relative to the baseURL and server-relative URLs
			// will be assumed to be relative to baseURL not the server root
			// if (uri.endsWith("/")) // always true
			// relativePath = relativePath.substring(1);
			uri += relativePath.substring(1); // strip off the leading '/'
		} else {
			// relative path is correctly relative so append to base URL
			uri += relativePath;
			//if (uri.endsWith("/")) uri += relativePath; // always true
			//else uri += "/" + relativePath;
		}
		return new URI(uri);
	}

	/**
	 * Load configuration
	 *
	 * @param config
	 * @throws IllegalArgumentException if any required configuration element is invalid or missing
	 * @throws IllegalStateException if authentication fails
	 * @exception NumberFormatException if any required string property does not contain a
	 *               parsable integer.
	 */
	public void load(XMLConfiguration config) {
		this.config = config;
		final String url = config.getString("baseURL");
		// see http://commons.apache.org/configuration/userguide/howto_xml.html
		if (StringUtils.isBlank(url)) {
			// TODO: if any tests don't require baseURL then may this optional and have tests check and mark status = SKIPPED
			throw new IllegalArgumentException("baseURL property must be defined");
		} else {
			try {
				baseURL = new URI(url);
				if (baseURL.getQuery() != null) {
					log.error("6.1.1 baseURL MUST NOT contain a query component, baseURL=" + baseURL);
				}
				baseUrlString = baseURL.toASCIIString();
				final String baseRawString = baseURL.toString();
				if (!baseUrlString.equals(baseRawString)) {
					log.warn("baseURL appears to have non-ASCII characters and comparisons using URL may have problems");
					log.debug("baseURL ASCII String=" + baseUrlString);
					log.debug("baseURL raw String=" + baseRawString);
				}
				if (!baseUrlString.endsWith("/")) baseUrlString += '/'; // end the baseURL with slash
			} catch (URISyntaxException e) {
				throw new IllegalArgumentException(e);
			}
		}

		// setup HTTP proxy if required
		String proxyHost = config.getString("proxy.host");
		String proxyPort = config.getString("proxy.port");
		if (StringUtils.isNotBlank(proxyHost) && StringUtils.isNotBlank(proxyPort)) {
			proxy = new HttpHost(proxyHost, Integer.parseInt(proxyPort), "http");
		}

		// load optional HttpRequestChecker for HTTP request handling
		final String httpRequestCheckerClass = config.getString("HttpRequestChecker");
		if (StringUtils.isNotBlank(httpRequestCheckerClass)) {
			try {
				Class httpClass = Class.forName(httpRequestCheckerClass);
				httpRequestChecker = (HttpRequestChecker) httpClass.newInstance();
				httpRequestChecker.setup(this);
                if (httpRequestChecker.getCurrentUser(this) != null)
                    currentUser = DEFAULT_USER;
			} catch (ClassNotFoundException e) {
				throw new IllegalArgumentException(e);
			} catch (InstantiationException e) {
				throw new IllegalArgumentException(e);
			} catch (IllegalAccessException e) {
				throw new IllegalArgumentException(e);
			}
		}
	}

	/**
	 * Get a string associated with the given configuration key.
	 * @param key The configuration key
	 * @return The associated string value if key is found otherwise null
	 */
	@CheckForNull
	public String getString(String key) {
		return config != null ? config.getString(key) : null;
	}

	/**
	 * Get user property which is stored as username . property name in configuration.
	 * @param user  The username alias, never null
	 * @param property The configuration key, never null
	 * @return The associated string value if key is found otherwise null
	 */
	@CheckForNull
	public String getUserProperty(String user, String property) {
		return getString(user + "." + property);
	}

	/**
	 * Get user property which is stored as username . property name in configuration.
	 * @param user  The username alias, never null
	 * @param property The configuration key, never null
	 * @param defaultValue The default value if property not found
	 * @return The associated string value if key is found otherwise defaultValue as provided
	 */
	public String getUserProperty(String user, String property, String defaultValue) {
		String value = getUserProperty(user, property);
		return value == null ? defaultValue : value;
	}

	public void setProperty(String key, String value) {
        if (config != null) {
            System.out.printf("XXX: set prop %s %s%n", key, value);//debug
            config.setProperty(key, value);
        }
    }

	/**
	 * Get named property as File from config.xml
	 * @param key The configuration key
	 * @return File or null if property does not found or file does not exist
	 */
	@CheckForNull
	public File getPropertyAsFile(String key) {
		String fileProp = getString(key);
		if (StringUtils.isBlank(fileProp)) {
			log.debug("property {} not found or contains empty string", key);
			return null;
		}
		log.trace(fileProp);
		File file = new File(fileProp);
		if (!file.isFile()) {
			log.info("file {} does not exist or isn't regular file", file);
			return null;
		}
        return file;
	}

	/**
	 * Get named property as URI from config.xml
	 * @param key The configuration key
	 * @return URI or null if property does not found or not valid URI
	 */
	@CheckForNull
	public URI getPropertyAsURI(String key) {
		String value = getString(key);
		if (StringUtils.isBlank(value)) {
			log.debug("property {} not found or contains empty string", key);
			return null;
		}
		log.trace(value);
		try {
			return new URI(value);
		} catch (URISyntaxException e) {
			log.warn(e.toString());
			return null;
		}
	}

	public SAXBuilder getBuilder(ErrorHandler errorHandler) {
		if (builder == null) {
			builder = new SAXBuilder(false);
			builder.setFeature(VALIDATION_FEATURE, false); // [false]
			builder.setFeature(SCHEMA_FULL_CHECKING_FEATURE, false); // [false]
			builder.setFeature(SCHEMA_VALIDATION_FEATURE, false); // [false]
			builder.setFeature(LOAD_DTD_GRAMMAR, false); // [true]
			builder.setFeature(LOAD_EXTERNAL_DTD, false); // [true]
			builder.setFeature("http://xml.org/sax/features/external-general-entities", false); // TRUE
			builder.setFeature("http://xml.org/sax/features/external-parameter-entities", false); // TRUE
			// http://xml.org/sax/features/namespace-prefixes [false]
			// builder.setFeature("http://xml.org/sax/features/namespaces", true); [true]
		}
		builder.setErrorHandler(errorHandler);
		return builder;
	}

	public SAXBuilder getValidatingBuilder(ErrorHandler errorHandler) {
		if (validatingBuilder == null) {
			validatingBuilder = new SAXBuilder(true);
			validatingBuilder.setFeature(VALIDATION_FEATURE, true);
			validatingBuilder.setFeature(SCHEMA_FULL_CHECKING_FEATURE, true);
			validatingBuilder.setFeature(SCHEMA_VALIDATION_FEATURE, true);
			validatingBuilder.setFeature(LOAD_DTD_GRAMMAR, false);
			validatingBuilder.setFeature(LOAD_EXTERNAL_DTD, false);
		}
		validatingBuilder.setErrorHandler(errorHandler);
		return validatingBuilder;
	}

	public HttpClient getHttpClient() {
		HttpClient client = new DefaultHttpClient();
		if (proxy != null) {
			// System.out.println("XXX: use HTTP proxy");
			client.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
		}
		return client;
	}

	/**
	 * Wrap <tt>HttpClient.execute()</tt> to pre/post-test HTTP requests for any
	 * server specific implementation handling such as authentication.
	 *
	 * @param client   the HttpClient, must never be null
	 * @param request   the request to execute, must never be null
	 *
	 * @return  the response to the request.
	 * @throws IOException in case of a problem or the connection was aborted
	 * @throws ClientProtocolException in case of an http protocol error
	 */
	public HttpResponse executeRequest(HttpClient client, HttpRequestBase request)
			throws IOException
	{
		if (httpRequestChecker != null) {
			return httpRequestChecker.executeRequest(this, client, request);
		} else {
			return client.execute(request);
		}
	}

    /**
     * Get current active user identity if applicable
     * @return user id (e.g. defaultUser) associated with active user context
     *          if applicable otherwise null
     */
    public String getUser() {
        return currentUser;
    }

    /**
     *
     * @param userId
     * @return true if successful sets user context, false otherwise
     */
    public boolean setUser(String userId) {
        if (userId != null && httpRequestChecker != null) {
            UserInfo userInfo = userMap.get(userId);
            if(userInfo == null) {
                String userEmail = getUserProperty(userId, "email");
                if (userEmail == null) {
                    log.warn("user " + userId + " email not found in config");
                    return false;
                }
                String password = getUserProperty(userId, "password");
                if (password == null) {
                    log.warn("user " + userId + " password not found in config");
                    return false;
                }
                userInfo = new UserInfo(userEmail, password);
                userMap.put(userId, userInfo);
            }
            try {
                //? currentUser = null; // null or keep last value ??
                httpRequestChecker.setUser(this, userId, userInfo.email, userInfo.password);
                if (userInfo.email.equals(httpRequestChecker.getCurrentUser(this))) {
                    currentUser = userId;
                    return true;
                }
			} catch(IllegalStateException e) {
				log.warn("failed to set user", e);
				return false;
            } catch(IllegalArgumentException e) {
                log.warn("failed to set user", e);
                return false;
            }
        }
        return false;
    }

    @NonNull
    public Reporter getReporter() {
        if (reporter == null) {
            reporter = new TextReporter();
        }
        return reporter;
    }

    public void setReporter(Reporter reporter) {
        if (reporter == null) throw new NullPointerException();
        this.reporter = reporter;
    }

	private static class UserInfo {
        private final String email;
        private final String password;
        public UserInfo(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }
}
