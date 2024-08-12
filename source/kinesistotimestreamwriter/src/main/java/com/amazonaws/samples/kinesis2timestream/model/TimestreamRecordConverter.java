package com.amazonaws.samples.kinesis2timestream.model;

import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.TimeZone;

import com.amazonaws.samples.kinesis2timestream.StreamingJob;
import org.json.JSONArray;
import org.json.JSONObject;
// import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.util.parsing.json.JSON;
import org.json.simple.parser.JSONParser;
import software.amazon.awssdk.services.timestreamwrite.model.Dimension;
import software.amazon.awssdk.services.timestreamwrite.model.MeasureValue;
import software.amazon.awssdk.services.timestreamwrite.model.MeasureValueType;
import software.amazon.awssdk.services.timestreamwrite.model.Record;
import software.amazon.awssdk.services.timestreamwrite.model.TimeUnit;
import com.github.wnameless.json.flattener.JsonFlattener;
import software.amazon.ion.Timestamp;


public class TimestreamRecordConverter {

    private static final Logger LOG = LoggerFactory.getLogger(StreamingJob.class);

    private static String OBJECT_TYPE           = "object_type";
    private static String OBJECT_EVENTS         = "events";
    private static String OBJECT_EVENT          = "event";
    private static String META_MAPPINGS         = "mappings";
    private static String META_MAPPINGS_SOURCE  = "source";
    private static String META_MAPPINGS_TARGET  = "target";
    private static String META_DIMENSIONS       = "dimensions";
    private static String META_DIMENSIONS_NAME  = "name";
    private static String META_MEASURES         = "measures";
    private static String META_MEASURES_NAME    = "name";
    private static String META_MEASURES_TYPE    = "type";
    private static String META_MEASURES_TYPE_V  = "VARCHAR";
    private static String META_MEASURES_TYPE_D  = "DOUBLE";
    private static String META_MEASURES_TYPE_B  = "BIGINT";

    private static String META_MEASURES_TIME    = "time";

    private class EventDimension {
        String name;
        String value;
    }

    private enum DataType {
        VARCHAR
        , DOUBLE
        , BIGINT
    }

    private static String transform(String key, JSONObject metaDetail) {
        JSONArray list = (JSONArray) metaDetail.get(META_MAPPINGS);

        for (Integer i=0; i< list.length(); i++) {
            JSONObject mapping = (JSONObject) list.get(i);

            if (mapping!=null) {
                String source = mapping.getString(META_MAPPINGS_SOURCE);
                if (source.equalsIgnoreCase(key)) {
                    String target = mapping.getString(META_MAPPINGS_TARGET);
                    return target;
                }
            }
        }

        // No transformation done, take key as target
        return key;
    }

    private static Boolean isDimension(String key, JSONObject metaDetail) {
        JSONArray dimensions = (JSONArray) metaDetail.get(META_DIMENSIONS);

        for (Integer i=0; i< dimensions.length(); i++) {
            JSONObject list = (JSONObject) dimensions.get(i);

            if (list!=null) {
                String name = list.getString(META_DIMENSIONS_NAME);
                if (name.equalsIgnoreCase(key)) {
                    return true;
                }
            }
        }


        return false;
    }

    private static DataType getDataType(String key, JSONObject metaDetail) {
        JSONArray measures = (JSONArray) metaDetail.get(META_MEASURES);

        for (Integer i=0; i< measures.length(); i++) {
            JSONObject element = (JSONObject) measures.get(i);

            if (element!=null) {
                String name = element.getString(META_MEASURES_NAME);
                if (name.equalsIgnoreCase(key)) {
                    String type = element.getString(META_MEASURES_TYPE);

                    if (type.equalsIgnoreCase(META_MEASURES_TYPE_V)) return DataType.VARCHAR;
                    else if (type.equalsIgnoreCase(META_MEASURES_TYPE_D)) return DataType.DOUBLE;
                    else if (type.equalsIgnoreCase(META_MEASURES_TYPE_B)) return DataType.BIGINT;
                }
            }
        }


        return null;
    }

    public static LocalDateTime convertDateWithFormat(String value, String format) {
        try {
//            SimpleDateFormat date = new SimpleDateFormat(format);
//            date.setTimeZone(TimeZone.getTimeZone("UTC"));
//            Date result = date.parse(value);
//            return result;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            LocalDateTime parsedDateTime = LocalDateTime.parse(value, formatter);
//            String microSecondsStr = value.substring(20);
//            int microSeconds = Integer.parseInt(microSecondsStr);
//            java.util.Date resultDate = java.util.Date.from(parsedDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant());
//            long resultTimeMillis = resultDate.getTime() + (microSeconds / 1000);
//            resultDate.setTime(resultTimeMillis);
//            System.out.println(resultDate);
            return parsedDateTime;

        }
        catch (Exception up) {
            return null;
        }
    }

    public static LocalDateTime convertDateFromValue(String value) {
        //millisec
        LocalDateTime result = convertDateWithFormat(value, "dd/MM/yyyy HH:mm:ss.SSS");
        if (result==null) result = convertDateWithFormat(value, "MMM dd, yyyy, HH:mm:ss.SSS"); //sec
        if (result==null) result = convertDateWithFormat(value, "MMM dd, yyyy, hh:mm:ss.SSS a"); // am/pm
        System.out.println(result);

        if (result==null)
            result = LocalDateTime.now();

        return result;
    }

    public static JSONObject flatten(String data) {
        // this function flattens the JSON tree into a key value par structure, to apply mapping
        // #TODO future feature: using JSONPath and or JOLT

        String flattenedJson = JsonFlattener.flatten(data);
        JSONObject flatten = new JSONObject(flattenedJson);
        
        return flatten;
    }

    public static Record convert(final EventMessage customObject, JSONObject eventMetaData) {
        try {
            if (customObject.getClass().equals(EventMessage.class)) {
                // return convertFromMetric((MyHostMetric) customObject);
                JSONObject dataRaw = new JSONObject(customObject.getData());

                JSONObject data = flatten(customObject.getData());

                String dMessage = "Message received : " + data.toString();
                LOG.error(dMessage);

                JSONObject meta = null;

                try {
                    meta = new JSONObject(customObject.getMetadata());
                    if (meta==null)
                        meta = eventMetaData;
                }
                catch (Exception up2) {
                    if (eventMetaData==null)
                        LOG.error("ERR-RC-1000 eventMetaData is null");
                    meta = eventMetaData;
                }
                // Object metaString = meta.get("events");
                // JSONArray eventMeta = new JSONArray(meta.get("events"));
                LOG.info("checking object type");
                String objectType = data.getString(OBJECT_TYPE);
                LOG.info("Object is " + objectType);

                JSONArray eventMeta = (JSONArray) meta.get(OBJECT_EVENTS);

                List<Dimension> timeStreamDimensions = new ArrayList<>();
                List<MeasureValue> measureValues = new ArrayList<>();

//                long secs = (new Date().getTime())/1000;
                long secs = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();
                Long lNow = secs;

                for (Integer i=0; i< eventMeta.length(); i++) {
                    String json = eventMeta.get(i).toString();

                    org.json.JSONObject geti = (org.json.JSONObject) eventMeta.get(i);

                    String sGeti = geti.toString();

                    JSONParser parser = new JSONParser();

                    Object obj = null;

                    try {
                        obj = parser.parse(sGeti);
                    }
                    catch (Exception e) {
                        LOG.error(e.getMessage());
                    }
                    org.json.simple.JSONObject metaDetail = (org.json.simple.JSONObject) obj;


                    // org.json.simple.JSONObject metaDetail = (org.json.simple.JSONObject) eventMeta.get(i);

                    org.json.JSONObject sMetaDetail = geti; // #TODO get from json above

                    if (metaDetail!=null) {
                        String event = sMetaDetail.getString(OBJECT_EVENT);
                        if (event != null && event.equalsIgnoreCase(objectType)) {
                            // found mapping, now iterate thru message data
                            Iterator<String> keys = data.keys();

                            // Loop below works for flat structures
                            while(keys.hasNext()) {
                                String key = keys.next();

                                String json2 = metaDetail.toJSONString();
                                JSONObject jMetaDetail = new JSONObject(json2);

                                String target = transform(key, jMetaDetail);

                                target = target.replace(".", "_");

                                if (isDimension(target, sMetaDetail)) {
                                    String value = data.getString(key);

                                    Dimension d = Dimension.builder()
                                            .name(target)
                                            .value(value)
                                            .build();

                                    timeStreamDimensions.add(d);
                                }
                                else {
                                    MeasureValueType valueType;

                                    DataType type = getDataType(target, sMetaDetail);
                                    String value = data.getString(key);

                                    if (key.equalsIgnoreCase(OBJECT_TYPE)) {
                                        // do nothing, ignore this attribute in measure, it was used to identify metadata
                                    }
                                    else if (key.equalsIgnoreCase(META_MEASURES_TIME)){
                                        LocalDateTime measureData = convertDateFromValue(value);
                                        Instant instant = measureData.toInstant(ZoneOffset.UTC);
                                        secs = instant.toEpochMilli();
                                        lNow = secs;
                                        System.out.println("Millisecond: "+ lNow);
                                    }
                                    else { // Measure is not time
                                        if (type==null) {
                                            valueType = MeasureValueType.VARCHAR;
                                        }
                                        else {
                                            switch (type) {
                                                case DOUBLE:
                                                    valueType = MeasureValueType.DOUBLE;
                                                    break;
                                                case BIGINT:
                                                    valueType = MeasureValueType.BIGINT;
                                                    break;
                                                case VARCHAR:
                                                default:
                                                    valueType = MeasureValueType.VARCHAR;
                                                    break;
                                            }
                                        }
                                        MeasureValue measureValue = MeasureValue.builder()
                                                .name(target)
                                                .type(valueType)
                                                .value(value)
                                                .build();
                                        measureValues.add(measureValue);
                                    }
                                }

                                String oValue = (String) data.get(key);
                                if (data.get(key) instanceof JSONObject) {
                                    String sValue = data.getString(key);
                                }
                            }
                        }
                    }
                }

                if (timeStreamDimensions.size()>0) {

                    Record record = Record.builder()
                            .dimensions(timeStreamDimensions)
                            .measureName(objectType)
                            .measureValueType("MULTI")
                            .measureValues(measureValues)
                            .timeUnit(TimeUnit.MILLISECONDS)
                            .time(Long.toString(lNow)).build();

                    LOG.error(record.toString()); // #TODO switch back to INFO
                    return record;
                }
                else {
                    LOG.error("Invalid message type 'object_type'=" + objectType);
                    return null;
                }
            } else {
                throw new RuntimeException("Invalid object type: " + customObject.getClass().getSimpleName());
            }
        }
        catch (Exception up) {
            throw up;
        }
    }

    private static String doubleToString(double inputDouble) {
        // Avoid sending -0.0 (negative double) to Timestream - it throws ValidationException
        if (Double.valueOf(-0.0).equals(inputDouble)) {
            return "0.0";
        }
        return Double.toString(inputDouble);
    }

}
