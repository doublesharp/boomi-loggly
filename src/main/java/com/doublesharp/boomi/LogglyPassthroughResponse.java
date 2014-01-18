package com.doublesharp.boomi;

import java.io.IOException;
import java.io.InputStream;

import com.boomi.connector.api.OperationStatus;

public class LogglyPassthroughResponse extends LogglyResponse {

	private InputStream input;

	public LogglyPassthroughResponse(InputStream input) {
		this.input = input;
	}

	@Override
	public InputStream getResponse() throws IOException {
		return input;
	}

	@Override
	public int getResponseCode() {
		// TODO Auto-generated method stub
		return 200;
	}

	@Override
	public String getResponseCodeAsString() {
		return "200";
	}

	@Override
	public String getResponseMessage() {
		return "OK";
	}

	@Override
	public OperationStatus getStatus() {
		return OperationStatus.SUCCESS;
	}

}
