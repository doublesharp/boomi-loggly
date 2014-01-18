package com.doublesharp.boomi;

import com.boomi.connector.api.BrowseContext;
import com.boomi.connector.api.Browser;
import com.boomi.connector.api.Operation;
import com.boomi.connector.api.OperationContext;
import com.boomi.connector.util.BaseConnector;

public class LogglyConnector extends BaseConnector {

	public LogglyConnector() {
		super();
	}

	@Override
	public Browser createBrowser(BrowseContext context) {
		return new LogglyBrowser(createConnection(context));
	}

	@Override
	protected Operation createCreateOperation(OperationContext context) {
		return new LogglyCreateOperation(createConnection(context));
	}

	private LogglyConnection createConnection(BrowseContext context) {
		return new LogglyConnection(context);
	}
}