package com.doublesharp.boomi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
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
import com.boomi.execution.ExecutionManager;
import com.boomi.execution.ExecutionTask;
import com.boomi.execution.ExecutionUtil;
import com.boomi.util.IOUtil;

public class LogglyCreateOperation extends BaseUpdateOperation {

	// string representing an http POST method
	private static final String POST_METHOD = "POST";
	// header property value for http content containing logging data
	private static final String PLAIN_CONTENT_TYPE = "text/plain";

	// properties which users can use to change for the Loggly operation
	protected static final String TAGS_PROPERTY = "tags";
	protected static final String PASSTHROUGH_PROPERTY = "passthrough";
	protected static final String LOGLEVEL_PROPERTY = "loglevel";
	protected static final String SEQUENCE_PROPERTY = "sequence";
	protected static final String PROCESS_PROPERTIES_PROPERTY = "processproperties";
	protected static final String DYNAMIC_PROPERTIES_PROPERTY = "dynamicproperties";
	protected static final String TOJSON_PROPERTY = "tojson";

	// default values for the properties
	private static final String DEFAULT_TAGS = "";
	private static final boolean DEFAULT_PASSTHROUGH = true;
	private static final String DEFAULT_LOGLEVEL = LogglyUtils.DEBUG;
	private static final boolean DEFAULT_TOJSON = false;
	private static final String DEFAULT_SEQUENCE = "START";
	private static final String DEFAULT_PROPERTIES = "";

	private static Map<String,Long> ticks = new HashMap<String,Long>();
	
	// we might append data to this variable
	private String tags;

	// final
	private final boolean _passthrough;
	private final String _sequence;
	private final String _processProperties;
	private final String _dynamicProperties;
	private final String _logLevel;
	private final boolean _toJson;

	protected LogglyCreateOperation(LogglyConnection conn) {
		super(conn);

		PropertyMap props = getContext().getOperationProperties();
		tags = props.getProperty(TAGS_PROPERTY, DEFAULT_TAGS);
		_passthrough = props.getBooleanProperty(PASSTHROUGH_PROPERTY, DEFAULT_PASSTHROUGH);
		_logLevel = props.getProperty(LOGLEVEL_PROPERTY, DEFAULT_LOGLEVEL);
		_toJson = props.getBooleanProperty(TOJSON_PROPERTY, DEFAULT_TOJSON);
		_sequence = props.getProperty(SEQUENCE_PROPERTY, DEFAULT_SEQUENCE);
		_processProperties = props.getProperty(PROCESS_PROPERTIES_PROPERTY, DEFAULT_PROPERTIES);
		_dynamicProperties = props.getProperty(DYNAMIC_PROPERTIES_PROPERTY, DEFAULT_PROPERTIES);
	}

	@Override
	protected void executeUpdate(UpdateRequest request, OperationResponse response) {
		final Logger logger = response.getLogger();
		final LogglyConnection connection = getConnection();
		// Add the sequence tag
		addTag(_sequence);
		// Add the log level tag
		if (connection._levelTag) {
			addTag(_logLevel);
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
						JSONObject xmlJSONObj = null;
						if (_toJson || connection._toJson) {
							try {
								logger.log(Level.INFO, "Convert XML to JSON");
								String text = LogglyUtils.getStringFromInputStream(is1);
								text = LogglyUtils.removeXmlStringNamespaceAndPreamble(text);
								xmlJSONObj = XML.toJSONObject(text);

								Calendar now = Calendar.getInstance();
								TimeZone tz = TimeZone.getTimeZone("UTC");
								DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
								df.setTimeZone(tz);	
								
								JSONObject boomi = new JSONObject();
								
								boomi.put("timestamp", df.format(now.getTime()));

								String processName = ExecutionManager.getCurrent().getProcessName();
								boomi.put("processName", processName);

								ExecutionTask task = ExecutionManager.getCurrent();
								
								String topLevelProcessId = task.getTopLevelProcessId();
								boomi.put("topLevelProcessId", topLevelProcessId);
								
								String componentId = task.getTopLevelComponentId();
								while (task!=null && !task.getComponentId().equals(componentId)){
									task = task.getParent();
								}
								if (task!=null){
									String parentProcessName = task.getProcessName();
									boomi.put("parentProcessName", parentProcessName);
								}

								String atomID = ExecutionUtil.getContainerId();
								boomi.put("atomId", atomID);

								String executionId = ExecutionManager.getCurrent().getExecutionId();
								boomi.put("executionId", executionId);
								
								String topLevelExecutionId = ExecutionManager.getCurrent().getTopLevelExecutionId();
								boomi.put("topLevelExecutionId", topLevelExecutionId);

								JSONObject jsonProperties = new JSONObject();
								
								// set dynamic process properties to json
								JSONObject dynamicProperties = new JSONObject();
								for (String property : _dynamicProperties.split(",")){
									property = property.trim();
									String propValue = ExecutionUtil.getDynamicProcessProperty(property);
									if (propValue==null){
										input.getUserDefinedProperties().get(property);
										if (propValue==null) propValue = "";
									}
									dynamicProperties.put(property, propValue);
								}
								jsonProperties.put("dynamic", dynamicProperties);
								
								// set process properties to json
								JSONObject processProperties = new JSONObject();
								for (String entries : _processProperties.split(",")){
									try {
										String[] entry = entries.split("=");
										String property = entry[0];
										String[] pair = entry[1].split(":");
										String id = pair[0].trim();
										String key = pair[1].trim();
										
										String propValue = ExecutionUtil.getProcessProperty(id, key);
										if (propValue==null) propValue = "null";
										processProperties.put(property, propValue);
									} catch (Exception e){
										logger.log(Level.SEVERE, LogglyUtils.getStackTrace(e));
									}
								}
								jsonProperties.put("process", processProperties);
								
								boomi.put("properties", jsonProperties);

								long nowMillis = now.getTimeInMillis();
								
								// try to get the start time from the cache
								Long startMillisObj = ticks.get(executionId);
								long startMillis = (startMillisObj==null)? nowMillis : startMillisObj;

								// cache a start tick
								if ("START".equals(_sequence)){
									ticks.put(executionId, nowMillis);
								} else 
								// put a tick into the log
								if ("TICK".equals(_sequence)){
									boomi.put("boomiTickTime", nowMillis-startMillis);
								} else {
									// anything else "error" or "end" will remove it from the Map. error on side of remove.
									boomi.put("boomiExecutionTime", nowMillis-startMillis);
									ticks.remove(executionId);
								}
								
								xmlJSONObj.put("boomi", boomi);
								
								String jsonPrettyPrintString = xmlJSONObj.toString(4);
								is1 = LogglyUtils.getInputStreamFromString(jsonPrettyPrintString);
							} catch (Exception e) {
								
								logger.log(Level.SEVERE, LogglyUtils.getStackTrace(e));
							}
							
						}
						// Construct a URL including our token
						final URL url = LogglyUtils.buildUrl(connection._baseUrl);
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

	private void addTag(String tag){
		if (tags.trim() == "")
			tags = tag;
		else
			tags += "," + tag;
	}
	
	@Override
	public LogglyConnection getConnection() {
		return (LogglyConnection) super.getConnection();
	}
}