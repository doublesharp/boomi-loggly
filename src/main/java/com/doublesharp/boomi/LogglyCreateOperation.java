package com.doublesharp.boomi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONObject;
import org.json.XML;

import com.boomi.connector.api.ObjectData;
import com.boomi.connector.api.OperationResponse;
import com.boomi.connector.api.PropertyMap;
import com.boomi.connector.api.ResponseUtil;
import com.boomi.connector.api.UpdateRequest;
import com.boomi.connector.util.BaseUpdateOperation;
import com.boomi.util.IOUtil;

public class LogglyCreateOperation extends BaseUpdateOperation {

	// string representing an http POST method
	private static final String POST_METHOD = "POST";
	// header property value for http content containing logging data
	private static final String PLAIN_CONTENT_TYPE = "text/plain";

	// properties which users can use to change for the Loggly operation
	protected static final String TAGS_PROPERTY = "tags";
	protected static final String ASYNC_PROPERTY = "async";
	protected static final String PASSTHROUGH_PROPERTY = "passthrough";
	protected static final String LOGLEVEL_PROPERTY = "loglevel";
	protected static final String TOJSON_PROPERTY = "tojson";

	// default values for the properties
	private static final String DEFAULT_TAGS = "";
	private static final boolean DEFAULT_PASSTHROUGH = true;
	private static final String DEFAULT_LOGLEVEL = LogglyUtils.DEBUG;
	private static final boolean DEFAULT_TOJSON = false;

	// we might append data to this variable
	private String tags;

	// final
	private final boolean _passthrough;
	private final String _logLevel;
	private final boolean _toJson;

	protected LogglyCreateOperation(LogglyConnection conn) {
		super(conn);

		PropertyMap props = getContext().getOperationProperties();
		tags = props.getProperty(TAGS_PROPERTY, DEFAULT_TAGS);
		_passthrough = props.getBooleanProperty(PASSTHROUGH_PROPERTY, DEFAULT_PASSTHROUGH);
		_logLevel = props.getProperty(LOGLEVEL_PROPERTY, DEFAULT_LOGLEVEL);
		_toJson = props.getBooleanProperty(TOJSON_PROPERTY, DEFAULT_TOJSON);
	}

	@Override
	protected void executeUpdate(UpdateRequest request, OperationResponse response) {
		Logger logger = response.getLogger();
		LogglyConnection connection = getConnection();
		// Add the log level to our tags
		if (connection._levelTag) {
			if (tags.trim() == "")
				tags = _logLevel;
			else
				tags += "," + _logLevel;
		}
		for (ObjectData input : request) {
			try {
				// Copy the data from the stream in case we want to pass it through.
				InputStream is = input.getData();
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				byte[] buffer = new byte[1024];
				int len;
				while ((len = is.read(buffer)) > -1) {
					baos.write(buffer, 0, len);
				}
				baos.flush();
				InputStream is1 = new ByteArrayInputStream(baos.toByteArray());

				LogglyResponse resp = null;

				// Disable all logging and pass data through.
				if (connection._disable) {
					logger.log(Level.INFO, "DISABLED \nTAGS:" + tags);
					resp = new LogglyPassthroughResponse(is1);
				} else {
					// Check to see if the Logging level on the connector is the same or lower than the operation
					try {
						Integer logPriority = LogglyUtils.LEVELS.get(_logLevel);
						Integer displayPriority = LogglyUtils.LEVELS.get(connection._logLevel);
						if (displayPriority > logPriority) {
							logger.log(Level.INFO, "Loggly.com: " + connection._logLevel + " > " + _logLevel + ", halting.");
							resp = new LogglyPassthroughResponse(is1);
						}
					} catch (NullPointerException npe) {
						if (LogglyUtils.LEVELS.get(_logLevel) == null)
							logger.log(Level.WARNING, "The log level '" + _logLevel + "' is not valid.");
						if (LogglyUtils.LEVELS.get(_logLevel) == null)
							logger.log(Level.WARNING, "The log display level '" + connection._logLevel + "' is not valid.");
					}

					// If there is no resp, get one from Loggly
					if (resp == null) {
						// Try to convert XML to JSON
						if (_toJson || connection._toJson) {
							try {
								logger.log(Level.INFO, "Convert XML to JSON");
								String text = LogglyUtils.getStringFromInputStream(is1);
								text = LogglyUtils.removeXmlStringNamespaceAndPreamble(text);
								JSONObject xmlJSONObj = XML.toJSONObject(text);

								xmlJSONObj.put("timeStamp", Calendar.getInstance().getTimeInMillis());

								String jsonPrettyPrintString = xmlJSONObj.toString(4);
								is1 = new ByteArrayInputStream(jsonPrettyPrintString.getBytes());
							} catch (Exception e) {
								logger.log(Level.WARNING, e.getMessage());
							}
						}

						// Construct a URL including our token
						URL url = LogglyUtils.buildUrl(connection._baseUrl);
						logger.log(Level.INFO, "POST: " + url + "\nTAGS: " + tags);

						// Send to Loggly
						resp = new LogglyResponse(LogglyUtils.send(url, POST_METHOD, PLAIN_CONTENT_TYPE, is1, tags));

						String msgComplete = "Loggly.com complete: " + resp.getResponseCode() + " " + resp.getResponseMessage();
						input.getLogger().log(Level.INFO, msgComplete);
						logger.log(Level.INFO, msgComplete);
					}
				}

				// dump the results into the response
				InputStream obj = null;
				try {
					obj = (_passthrough) ? new ByteArrayInputStream(baos.toByteArray()) : resp.getResponse();
					if (obj != null) {
						response.addResult(input, resp.getStatus(), resp.getResponseCodeAsString(), resp.getResponseMessage(), ResponseUtil.toPayload(obj));
					} else {
						response.addEmptyResult(input, resp.getStatus(), resp.getResponseCodeAsString(), resp.getResponseMessage());
					}

				} finally {
					IOUtil.closeQuietly(obj);
				}

			} catch (Exception e) {
				// make best effort to process every input
				ResponseUtil.addExceptionFailure(response, input, e);
			}
		}
	}

	@Override
	public LogglyConnection getConnection() {
		return (LogglyConnection) super.getConnection();
	}
}