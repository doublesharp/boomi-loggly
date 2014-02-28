package com.doublesharp.boomi;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

import com.boomi.connector.api.OperationStatus;

public class LogglyResponse {

	private final HttpURLConnection _conn;
	private final int _responseCode;
	private final String _responseMsg;

	public LogglyResponse() {
		_conn = null;
		_responseCode = 0;
		_responseMsg = null;
	}
	
	public LogglyResponse(int errorCode, String message) {
		_conn = null;
		_responseCode = errorCode;
		_responseMsg = message;
	}

	public LogglyResponse(HttpURLConnection conn) throws IOException {
		_conn = conn;
		_responseCode = (conn != null) ? conn.getResponseCode() : 0;
		_responseMsg = (conn != null) ? conn.getResponseMessage() : null;
	}

	public InputStream getResponse() throws IOException {
		if (_conn!=null){
			try {
				return _conn.getInputStream();
			} catch (IOException e) {
				if (OperationStatus.SUCCESS == getStatus()) {
					// bummer, that's not good
					throw e;
				}
				return _conn.getErrorStream();
			}
		} else {
			return null;
		}
	}

	public int getResponseCode() {
		return _responseCode;
	}

	public String getResponseCodeAsString() {
		return String.valueOf(_responseCode);
	}

	public String getResponseMessage() {
		return _responseMsg;
	}

	/**
	 * Returns the OperationStatus for the given http code.
	 * 
	 * @param httpCode
	 *            an http code
	 * 
	 * @return SUCCESS if the code indicates success, FAILURE otherwise
	 */
	public OperationStatus getStatus() {
		// success: 200 <= code < 300
		if (_responseCode >= HttpURLConnection.HTTP_OK && _responseCode < HttpURLConnection.HTTP_MULT_CHOICE) {
			return OperationStatus.SUCCESS;
		} else if (_responseCode >= HttpURLConnection.HTTP_MULT_CHOICE && _responseCode < HttpURLConnection.HTTP_INTERNAL_ERROR) {
			return OperationStatus.APPLICATION_ERROR;
		} else {
			return OperationStatus.FAILURE;
		}
	}
}
