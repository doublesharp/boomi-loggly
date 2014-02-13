package com.doublesharp.boomi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONObject;
import org.json.XML;

import sun.awt.geom.Crossings;

import com.boomi.connector.api.ObjectData;
import com.boomi.connector.api.OperationResponse;
import com.boomi.connector.api.PropertyMap;
import com.boomi.connector.api.ResponseUtil;
import com.boomi.connector.api.UpdateRequest;
import com.boomi.connector.util.BaseUpdateOperation;
import com.boomi.execution.ExecutionManager;
import com.boomi.execution.ExecutionTask;
import com.boomi.execution.ExecutionUtil;
import com.boomi.function.lookup.CrossRefLookup;
import com.boomi.util.IOUtil;

public class LogglyCreateOperation extends BaseUpdateOperation {

	// string representing an http POST method
	private static final String POST_METHOD = "POST";
	
	// header property value for http content containing logging data
	private static final String PLAIN_CONTENT_TYPE = "text/plain";

	// properties which users can use to change for the Loggly operation
	protected static final String PASSTHROUGH_PROPERTY = "passthrough";
	protected static final String LOGLEVEL_PROPERTY = "loglevel";
	protected static final String SEQUENCE_PROPERTY = "sequence";
	protected static final String TOJSON_PROPERTY = "tojson";
	protected static final String TAGS_PROPERTY = "tags";
	protected static final String PROCESS_PROPERTIES_PROPERTY = "processproperties";
	protected static final String DYNAMIC_PROPERTIES_PROPERTY = "dynamicproperties";

	// default values for the properties
	private static final String DEFAULT_TAGS = "";
	private static final boolean DEFAULT_PASSTHROUGH = true;
	private static final String DEFAULT_LOGLEVEL = LogglyUtils.DEBUG;
	private static final boolean DEFAULT_TOJSON = false;
	private static final String DEFAULT_SEQUENCE = "START";
	private static final String DEFAULT_PROPERTIES = "";

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
		logger.info("Loggly.com: Sequence " + _sequence);
		// Add the log level tag
		if (connection._levelTag) {
			addTag(_logLevel);
		}
		// Loop through each of the input documents
		for (ObjectData input : request) {
			try {
				// Copy the data from the stream in case we want to pass it
				// through.
				InputStream is = input.getData();
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				byte[] buffer = new byte[1024];
				int len;
				while ((len = is.read(buffer)) > -1) {
					baos.write(buffer, 0, len);
				}
				baos.flush();

				// reset the input stream
				InputStream isCopy = new ByteArrayInputStream(baos.toByteArray());

				// our response with status, data, etc
				LogglyResponse resp = null;

				// Disable all logging and pass data through.
				if (connection._disable) {
					logger.log(Level.INFO, "DISABLED \nTAGS:" + tags);
					resp = new LogglyPassthroughResponse(isCopy);
				} else {
					// Check to see if the Logging level on the connector is the
					// same or lower than the operation
					try {
						Integer logPriority = LogglyUtils.LEVELS.get(_logLevel);
						Integer displayPriority = LogglyUtils.LEVELS.get(connection._logLevel);
						if (displayPriority > logPriority) {
							logger.log(Level.INFO, "Loggly.com: " + connection._logLevel + " > " + _logLevel + ", halting.");
							resp = new LogglyPassthroughResponse(isCopy);
						}
					} catch (NullPointerException npe) {
						if (LogglyUtils.LEVELS.get(_logLevel) == null)
							logger.log(Level.WARNING, "The log level '" + _logLevel + "' is not valid.");
						if (LogglyUtils.LEVELS.get(_logLevel) == null)
							logger.log(Level.WARNING, "The log display level '" + connection._logLevel + "' is not valid.");
					}

					// If there is no resp, get one from Loggly
					if (resp == null) {
						JSONObject baseJSON = new JSONObject();
						try {
							// Boomi process metadata
							JSONObject boomi = new JSONObject();

							// Timestamp in ISO 8601
							Calendar now = Calendar.getInstance();
							TimeZone tz = TimeZone.getTimeZone("UTC");
							DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
							df.setTimeZone(tz);
							boomi.put("timestamp", df.format(now.getTime()));

							// Name of the process this logging is coming from
							String processName = ExecutionManager.getCurrent().getProcessName();
							boomi.put("processName", processName);

							// The current running process/task
							ExecutionTask task = ExecutionManager.getCurrent();

							// The top level process if this is a subprocess
							String topLevelProcessId = task.getTopLevelProcessId();
							boomi.put("topLevelProcessId", topLevelProcessId);

							// Determine the name of the top level process if
							// this is a subprocess
							String componentId = task.getTopLevelComponentId();
							while (task != null && !task.getComponentId().equals(componentId)) {
								task = task.getParent();
							}
							if (task != null) {
								String parentProcessName = task.getProcessName();
								boomi.put("parentProcessName", parentProcessName);
							}

							// The atom
							String atomID = ExecutionUtil.getContainerId();
							boomi.put("atomId", atomID);

							// The execution (may be a thread)
							String executionId = ExecutionManager.getCurrent().getExecutionId();
							boomi.put("executionId", executionId);

							// The parent execution (may be a thread)
							String topLevelExecutionId = ExecutionManager.getCurrent().getTopLevelExecutionId();
							boomi.put("topLevelExecutionId", topLevelExecutionId);

							// All the different properties this process might
							// use (process, dynamic, document)
							JSONObject jsonProperties = new JSONObject();

							// set dynamic process properties to JSON
							String dynaProps = ("".equals(_dynamicProperties)) ? connection._dynamicProperties : ("".equals(connection._dynamicProperties)) ? _dynamicProperties : _dynamicProperties
									+ connection._dynamicProperties;
							JSONObject dynamicProperties = new JSONObject();
							for (String property : dynaProps.split(",")) {
								property = property.trim();
								String propValue = ExecutionUtil.getDynamicProcessProperty(property);
								if (propValue == null) {
									input.getUserDefinedProperties().get(property);
									if (propValue == null)
										propValue = "";
								}
								dynamicProperties.put(property, propValue);
							}
							jsonProperties.put("dynamic", dynamicProperties);

							// set process properties to JSON

							
							String procProps = ("".equals(_processProperties)) ? 
								connection._processProperties : 
								("".equals(connection._processProperties)) ? 
									_processProperties : 
									_processProperties + connection._processProperties;
							JSONObject processProperties = new JSONObject();
							for (String entries : procProps.split(",")) {
								//
								try {
									String[] entry = entries.split("=");
									String property = entry[0];
									String[] pair = entry[1].split(":");
									String id = pair[0].trim();
									String key = pair[1].trim();

									String propValue = ExecutionUtil.getProcessProperty(id, key);
									if (propValue == null)
										propValue = "";
									processProperties.put(property, propValue);
								} catch (Exception e) {
									logger.log(Level.SEVERE, LogglyUtils.getStackTrace(e));
								}
							}
							jsonProperties.put("process", processProperties);

							// Add properties to the metadata
							boomi.put("properties", jsonProperties);

							// calculate timing
							long nowMillis = now.getTimeInMillis();
							final String timerKey = executionId + "_timer";
							String startMillisObj = ExecutionUtil.getDynamicProcessProperty(timerKey);
							long startMillis = (startMillisObj == null) ? nowMillis : Long.valueOf(startMillisObj);
							if ("START".equalsIgnoreCase(_sequence)) {
								// cache a start tick
								ExecutionUtil.setDynamicProcessProperty(timerKey, String.valueOf(startMillis), false);
							} else if ("TICK".equalsIgnoreCase(_sequence)) {
								// put a tick into the log
								boomi.put("tickTime", nowMillis - startMillis);
							} else if ("END".equalsIgnoreCase(_sequence) || "ERROR".equalsIgnoreCase(_sequence)) {
								// "error" or "end" will set the total
								// executionTime
								boomi.put("executionTime", nowMillis - startMillis);
							}

							// Include all the Boomi metadata in the base
							baseJSON.put("boomi", boomi);

							// Deal with the actual input
							String text = LogglyUtils.getStringFromInputStream(isCopy, logger);
							JSONObject xmlJSONObj = null;

							// Try to convert XML to JSON?
							if (_toJson || connection._toJson) {
								try {
									logger.info("Convert XML to JSON");
									xmlJSONObj = XML.toJSONObject(LogglyUtils.removeXmlStringNamespaceAndPreamble(text));
								} catch (Exception e) {
									logger.warning("Failed to convert XML to JSON");
								}
							}
							// Add the data on its own element, converted to
							// JSON if available otherwise the straight input
							// text
							baseJSON.put("data", (xmlJSONObj != null) ? xmlJSONObj : text);

							// Copy the InputStream for the response
							isCopy = LogglyUtils.getInputStreamFromString(baseJSON.toString(4));
						} catch (Exception e) {
							logger.severe(LogglyUtils.getStackTrace(e));
						}
					}
					// Construct a URL including our token
					final URL url = LogglyUtils.buildUrl(connection._baseUrl);
					logger.info( "POST: " + url + "\nTAGS: " + tags);

					// Send to Loggly, we should have updated is1 by now
					resp = new LogglyResponse(LogglyUtils.send(url, POST_METHOD, PLAIN_CONTENT_TYPE, isCopy, tags));
					String msgComplete = "Loggly.com complete: " + resp.getResponseCode() + " " + resp.getResponseMessage();
					input.getLogger().log(Level.INFO, msgComplete);
					logger.info(msgComplete);
				}

				// dump the results into the response
				InputStream obj = null;
				try {
					// if this is a pass-through get a new InputStream
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

	private void addTag(String tag) {
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