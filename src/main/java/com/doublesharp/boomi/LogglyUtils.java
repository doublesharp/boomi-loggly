package com.doublesharp.boomi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.boomi.util.IOUtil;
import com.boomi.util.StreamUtil;
import com.boomi.util.URLUtil;

public abstract class LogglyUtils {

	public static final String DEBUG = "DEBUG";
	public static final String INFO = "INFO";
	public static final String WARN = "WARN";
	public static final String ERROR = "ERROR";

	public static final Map<String, Integer> LEVELS;
	static {
		Map<String, Integer> aMap = new HashMap<String, Integer>();
		aMap.put(DEBUG, 1);
		aMap.put(INFO, 2);
		aMap.put(WARN, 3);
		aMap.put(ERROR, 4);
		LEVELS = Collections.unmodifiableMap(aMap);
	}

	/** property name for the http content type header */
	private static final String CONTENT_TYPE_HEADER = "Content-Type";
	private static final String X_LOGGLY_TAG_HEADER = "X-LOGGLY-TAG";

	public static URL buildUrl(String... components) throws IOException {
		return buildUrl((List<Map.Entry<String, String>>) null, components);
	}

	public static URL buildUrl(List<Map.Entry<String, String>> params, String... components) throws IOException {
		// generate url w/ path
		return URLUtil.makeUrl(params, (Object[]) components);
	}

	public static HttpURLConnection send(URL url, String requestMethod, String contentType, InputStream data, String tags) throws IOException {
		try {
			HttpURLConnection conn = prepareSend(url, requestMethod, contentType, tags);

			OutputStream out = conn.getOutputStream();
			try {
				StreamUtil.copy(data, out);
			} finally {
				out.close();
			}

			return conn;
		} finally {
			IOUtil.closeQuietly(data);
		}
	}

	private static HttpURLConnection prepareSend(URL url, String requestMethod, String contentType, String tags) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod(requestMethod);
		conn.setDoOutput(true);
		conn.setDoInput(true);
		conn.setRequestProperty(CONTENT_TYPE_HEADER, contentType);
		conn.setRequestProperty(X_LOGGLY_TAG_HEADER, tags);
		return conn;
	}

	// convert InputStream to String
	public static String getStringFromInputStream(InputStream is) {
		BufferedReader br = null;
		StringBuilder sb = new StringBuilder();

		String line;
		try {
			br = new BufferedReader(new InputStreamReader(is));
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		return sb.toString();
	}

	// remove "extra" data from xml, used for converting to JSON
	public static String removeXmlStringNamespaceAndPreamble(String xmlString) {
		return xmlString.replaceAll("(<\\?[^<]*\\?>)?", "") // remove preamble
		.replaceAll("\\s?xmlns.*?(\"|\').*?(\"|\')", "") 	// remove xmlns declaration
		.replaceAll("(<)(\\w+:)(.*?>)", "$1$3") 			// remove opening tag prefix
		.replaceAll("(</)(\\w+:)(.*?>)", "$1$3"); 			// remove closing tags prefix
	}
}
