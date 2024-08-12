package com.amazonaws.samples.kinesis2timestream.model;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
// import org.json.JSONObject;
import org.json.simple.JSONObject;

public class TimestreamRecordDeserializer implements DeserializationSchema<EventMessage> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    private JSONObject metadata;

    public TimestreamRecordDeserializer(JSONObject jsonObject) {
        this.metadata = jsonObject;
    }

    @Override
    public EventMessage deserialize(byte[] messageBytes) {
        try {
            String strData = new String(messageBytes, StandardCharsets.UTF_8);
            EventMessage event = new EventMessage();
            event.setData(strData);
            event.setMetadata(metadata);
            return event;
            // return objectMapper.readValue(messageBytes, EventMessage.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize message", e);
        }
    }

    @Override
    public boolean isEndOfStream(EventMessage nextElement) {
        return false;
    }

    @Override
    public TypeInformation<EventMessage> getProducedType() {
        return TypeInformation.of(EventMessage.class);
    }
}
