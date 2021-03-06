package com.doublesharp.boomi;

import com.boomi.connector.api.BrowseContext;
import com.boomi.connector.api.PropertyMap;
import com.boomi.connector.util.BaseConnection;

public class LogglyConnection extends BaseConnection {

	private static final String FORWARD_SLASH = "/";
	
	// properties which users can use to change for the Loggly connection
	private static final String URL_PROPERTY = "url";
	private static final String TOKEN_PROPERTY = "token";
	protected static final String DISABLE_PROPERTY = "disable";
	protected static final String LEVELTAG_PROPERTY = "leveltag";
	protected static final String LOGLEVEL_PROPERTY = "loglevel";
	protected static final String TOJSON_PROPERTY = "tojson";
	protected static final String TAGS_PROPERTY = "tags";
	protected static final String PROCESS_PROPERTY = "processproperties";
	protected static final String TIMEOUT_PROPERTY = "timeout";

	// default values for the properties
	private static final boolean DEFAULT_DISABLE = false;
	private static final boolean DEFAULT_LEVELTAG = true;
	private static final String DEFAULT_LOGLEVEL = LogglyUtils.DEBUG;
	private static final boolean DEFAULT_TOJSON = false;
	private static final String DEFAULT_PROPERTIES = "";
	private static final long DEFAULT_TIMEOUT = 2500; // 2.5 sec timeout
	private static final String DEFAULT_URL = "https://logs-01.loggly.com/inputs/";

	// instance variables for this connection
    protected final String _url;
    protected final String _token;
	protected final String _baseUrl;
	protected final boolean _disable;
	protected final boolean _toJson;
	protected final boolean _levelTag;
	protected final String _logLevel;
	protected final String _tags;
	protected final String _processProperties;
	protected final int _timeout;
	
	
	// construct a new instance
	public LogglyConnection(BrowseContext context) {
		super(context);
		
		// get the properties from the context and set
		PropertyMap props = context.getConnectionProperties();
		_url = props.getProperty(URL_PROPERTY, DEFAULT_URL);
		_token = props.getProperty(TOKEN_PROPERTY);
		_baseUrl = _url + _token + FORWARD_SLASH;
		_disable = props.getBooleanProperty(DISABLE_PROPERTY, DEFAULT_DISABLE);
		_levelTag = props.getBooleanProperty(LEVELTAG_PROPERTY, DEFAULT_LEVELTAG);
		_logLevel = props.getProperty(LOGLEVEL_PROPERTY, DEFAULT_LOGLEVEL);
		_toJson = props.getBooleanProperty(TOJSON_PROPERTY, DEFAULT_TOJSON);
		_tags = props.getProperty(TAGS_PROPERTY, DEFAULT_PROPERTIES);
		_processProperties = props.getProperty(PROCESS_PROPERTY, DEFAULT_PROPERTIES);
		int timeout = (int) DEFAULT_TIMEOUT;
		try {
			timeout = (int) props.getLongProperty(TIMEOUT_PROPERTY, DEFAULT_TIMEOUT).longValue();
		} catch (Exception e){}
		_timeout = timeout;
	}
}