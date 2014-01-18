package com.doublesharp.boomi;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.json.JSONObject;
import org.json.XML;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@RunWith(JUnit4.class)
public class LogglyConnectorTest {

	@SuppressWarnings("unused")
	@Test
    public void testCreateOperation() throws Exception
    {
		/*
        LogglyConnector connector = new LogglyConnector();  
      
        ConnectorTester tester = new ConnectorTester(connector);

        Map<String, Object> cProps = new HashMap<String, Object>();
        cProps.put(LogglyConnection.DISABLE_PROPERTY, false);
        cProps.put(LogglyConnection.TOJSON_PROPERTY, false);
        cProps.put(LogglyConnection.LOGLEVEL_PROPERTY, LogglyUtils.DEBUG);
        
        Map<String, Object> oProps = new HashMap<String, Object>();
        oProps.put(LogglyCreateOperation.PASSTHROUGH_PROPERTY, true);
        oProps.put(LogglyCreateOperation.LOGLEVEL_PROPERTY, "DEBUG");
        oProps.put(LogglyCreateOperation.TAGS_PROPERTY, "test");

        // setup the operation context for a GET operation on an object with type "SomeType"
        tester.setOperationContext(OperationType.CREATE, cProps, oProps, "entry", null);

        String inputString = "this is a test and just a test";
        List<InputStream> is = new ArrayList<InputStream>();
        is.add(new ByteArrayInputStream(inputString.getBytes()));

        tester.testExecuteCreateOperation(is, new ArrayList<SimpleOperationResult>());
        */
		
		
		
		String text = "<ns1:MembershipNumberRequest xmlns:ns1=\"http://www.boomi.com/v1b/AAA\"><ns1:State>CA</ns1:State></ns1:MembershipNumberRequest>";
		System.out.println(text);
		text = LogglyUtils.removeXmlStringNamespaceAndPreamble(text);
		System.out.println(text);
		JSONObject xmlJSONObj = XML.toJSONObject(text);
        String jsonPrettyPrintString = xmlJSONObj.toString(4);

        InputStream data = new ByteArrayInputStream(jsonPrettyPrintString.getBytes());
        	
        String theString = LogglyUtils.getStringFromInputStream(data);
        
        
       // String retext = StreamUtil.toString(data, "UTF-8");
    }
}
