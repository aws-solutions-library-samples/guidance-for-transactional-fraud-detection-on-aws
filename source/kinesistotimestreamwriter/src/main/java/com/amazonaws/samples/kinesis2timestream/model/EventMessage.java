package com.amazonaws.samples.kinesis2timestream.model;

// import org.json.simple.JSONObject;

// import org.json.JSONObject;

import org.json.simple.JSONObject;

public class EventMessage {
    private String data;
    private JSONObject metadata;

    public JSONObject getMetadata() {
        return metadata;
    }

    public void setMetadata(JSONObject metadata) {
        this.metadata = metadata;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
