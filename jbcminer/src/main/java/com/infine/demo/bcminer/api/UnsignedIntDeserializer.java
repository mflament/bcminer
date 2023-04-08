package com.infine.demo.bcminer.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;

import java.io.IOException;

public class UnsignedIntDeserializer extends StdScalarDeserializer<Integer> {
    public UnsignedIntDeserializer() {
        super(Integer.TYPE);
    }

    @Override
    public Integer deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        if (jsonParser.currentToken().isNumeric())
            return Integer.parseUnsignedInt(jsonParser.getValueAsString());
        deserializationContext.handleUnexpectedToken(handledType(), jsonParser);
        return null;
    }
}
