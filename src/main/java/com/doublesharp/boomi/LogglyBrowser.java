package com.doublesharp.boomi;

import java.util.Collection;

import com.boomi.connector.api.ConnectorException;
import com.boomi.connector.api.ObjectDefinition;
import com.boomi.connector.api.ObjectDefinitionRole;
import com.boomi.connector.api.ObjectDefinitions;
import com.boomi.connector.api.ObjectType;
import com.boomi.connector.api.ObjectTypes;
import com.boomi.connector.util.BaseBrowser;

public class LogglyBrowser extends BaseBrowser {

	protected LogglyBrowser(LogglyConnection conn) {
		super(conn);
	}

	@Override
	public ObjectDefinitions getObjectDefinitions(String objectTypeId, Collection<ObjectDefinitionRole> roles) {
		try {
			// parse the returned XML Schema document and construct an ObjectDefinition
			// Document defDoc = getConnection().getMetadata(objectTypeId);
			ObjectDefinitions defs = new ObjectDefinitions();
			ObjectDefinition def = new ObjectDefinition();
			// def.setSchema(defDoc.getDocumentElement());
			def.setElementName(objectTypeId);
			defs.getDefinitions().add(def);

			return defs;

		} catch (Exception e) {
			throw new ConnectorException(e);
		}

	}

	@Override
	public ObjectTypes getObjectTypes() {
		try {
			ObjectTypes types = new ObjectTypes();

			ObjectType type = new ObjectType();
			type.setId("entry");
			types.getTypes().add(type);

			return types;

		} catch (Exception e) {
			throw new ConnectorException(e);
		}
	}

	@Override
	public LogglyConnection getConnection() {
		return (LogglyConnection) super.getConnection();
	}
}