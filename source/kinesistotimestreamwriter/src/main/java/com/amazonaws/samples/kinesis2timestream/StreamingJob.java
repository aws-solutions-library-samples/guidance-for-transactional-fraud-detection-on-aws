/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amazonaws.samples.kinesis2timestream;

import java.io.FileNotFoundException;
import java.time.Duration;
import java.util.*;
import java.io.FileReader;
import java.io.IOException;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
//import org.json.simple.JSONObject;
// import org.json.JSONObject;
import org.json.simple.JSONObject;
// import org.json.simple.parser.JSONParser;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.amazonaws.samples.kinesis2timestream.kinesis.RoundRobinKinesisShardAssigner;
import com.amazonaws.samples.kinesis2timestream.model.EventMessage;
// import com.amazonaws.samples.kinesis2timestream.model.MyHostBase;
import com.amazonaws.samples.kinesis2timestream.model.TimestreamRecordConverter;
import com.amazonaws.samples.kinesis2timestream.utils.ParameterToolUtils;
import com.amazonaws.samples.kinesis2timestream.model.TimestreamRecordDeserializer;
import com.amazonaws.samples.connectors.timestream.TimestreamSinkConfig;
import com.amazonaws.samples.connectors.timestream.TimestreamSink;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.config.AWSConfigConstants;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.timestreamwrite.model.Record;
import software.amazon.awssdk.services.timestreamwrite.model.WriteRecordsRequest;

/**
 * Skeleton for a Flink Streaming Job.
 *
 * <p>For a tutorial how to write a Flink streaming application, check the
 * tutorials and examples on the <a href="http://flink.apache.org/docs/stable/">Flink Website</a>.
 *
 * <p>To package your application into a JAR file for execution, run
 * 'mvn clean package' on the command line.
 *
 * <p>If you change the name of the main class (with the public static void main(String[] args))
 * method, change the respective entry in the POM.xml file (simply search for 'mainClass').
 */
public class StreamingJob {

	private static final Logger LOG = LoggerFactory.getLogger(StreamingJob.class);

	// Currently Timestream supports max. 100 records in single write request. Do not increase this value.
	private static final int MAX_TIMESTREAM_RECORDS_IN_WRITERECORDREQUEST = 100;
	private static final int MAX_CONCURRENT_WRITES_TO_TIMESTREAM = 1000;

	// private static final String DEFAULT_STREAM_NAME = "TimestreamTestStream";
	private static final String DEFAULT_STREAM_NAME = "IOT_Data_KDS";
	private static final String DEFAULT_REGION_NAME = "us-east-1";

	private static JSONObject readMetadata(String file) {
		JSONParser parser = new JSONParser();

		try {
			Object obj = parser.parse(new FileReader(file));
			org.json.simple.JSONObject so = (org.json.simple.JSONObject) obj;
			String json = so.toJSONString();

			JSONObject jsonObject = so; //new JSONObject(json);;

			return jsonObject;

			/*
			String name = (String) jsonObject.get("name");
			System.out.println(name);

			String city = (String) jsonObject.get("city");
			System.out.println(city);

			String job = (String) jsonObject.get("job");
			System.out.println(job);

			// loop array
			JSONArray cars = (JSONArray) jsonObject.get("cars");
			Iterator<String> iterator = cars.iterator();
			while (iterator.hasNext()) {
				System.out.println(iterator.next());
			}*/
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return null;
	}

	static JSONObject eventMetaData;
	static org.json.JSONObject jEventMetaData;

	public static DataStream<EventMessage> createKinesisSource(
			StreamExecutionEnvironment env,
			ParameterTool parameter,
			String region, String inputStreamName) throws Exception {

		//set Kinesis consumer properties
		Properties kinesisConsumerConfig = new Properties();
		//set the region the Kinesis stream is located in

		//kinesisConsumerConfig.setProperty(AWSConfigConstants.AWS_REGION,
		//		parameter.get("Region", DEFAULT_REGION_NAME));

		kinesisConsumerConfig.setProperty(AWSConfigConstants.AWS_REGION, region);

		//obtain credentials through the DefaultCredentialsProviderChain, which includes the instance metadata
		kinesisConsumerConfig.setProperty(AWSConfigConstants.AWS_CREDENTIALS_PROVIDER, "AUTO");

		String adaptiveReadSettingStr = parameter.get("SHARD_USE_ADAPTIVE_READS", "false");

		if(adaptiveReadSettingStr.equals("true")) {
			kinesisConsumerConfig.setProperty(ConsumerConfigConstants.SHARD_USE_ADAPTIVE_READS, "true");
		} else {
			//poll new events from the Kinesis stream once every second
			kinesisConsumerConfig.setProperty(ConsumerConfigConstants.SHARD_GETRECORDS_INTERVAL_MILLIS,
					parameter.get("SHARD_GETRECORDS_INTERVAL_MILLIS", "1000"));
			// max records to get in shot
			kinesisConsumerConfig.setProperty(ConsumerConfigConstants.SHARD_GETRECORDS_MAX,
					parameter.get("SHARD_GETRECORDS_MAX", "10000"));
		}

		//create Kinesis source
		FlinkKinesisConsumer<EventMessage> flinkKinesisConsumer = new FlinkKinesisConsumer<>(
				//read events from the Kinesis stream passed in as a parameter
				// parameter.get("InputStreamName", DEFAULT_STREAM_NAME),
				inputStreamName,
				//deserialize events with EventSchema
				new TimestreamRecordDeserializer(eventMetaData),
				//using the previously defined properties
				kinesisConsumerConfig
		);
		flinkKinesisConsumer.setShardAssigner(new RoundRobinKinesisShardAssigner());

		return env
				.addSource(flinkKinesisConsumer)
				.name("KinesisSource");
	}

	public static void main(String[] args) throws Exception {

		ParameterTool parameter = ParameterToolUtils.fromArgsAndApplicationProperties(args);

		// set up the streaming execution environment
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		Boolean parametersLoaded = true;
		String region = null;
		String databaseName = null;
		String tableName = null;
		String inputStreamName = null;
		long memoryStoreTTLHours = 0;
		long magneticStoreTTLDays = 0;

		int TIMESTREAM_RECORDS_IN_WRITERECORDREQUEST = MAX_TIMESTREAM_RECORDS_IN_WRITERECORDREQUEST;
		String Batchload = parameter.get("Batchload");
		try{
			int size = Integer.parseInt(Batchload);
			if(size < 100){
				TIMESTREAM_RECORDS_IN_WRITERECORDREQUEST = size;
			}
		}catch (Exception e){
			System.out.println("Set to default MAX, Batchload = 100");
		}
		// Load mapping configuration
		String metadata = parameter.get("Metadata");
		if (metadata!=null && !metadata.isEmpty()) {
			eventMetaData = readMetadata(metadata);
			String json = eventMetaData.toJSONString();
			jEventMetaData = new org.json.JSONObject(json);
			/*
			try {
				File myObj = new File(metadata);
				if (myObj.createNewFile()) {
					System.out.println("File created: " + myObj.getName());
				} else {
					System.out.println("File already exists.");
				}
			}
			catch (Exception up) {
				System.out.println("errror");
			}*/
		}
		else {
			Map<String, Properties> applicationProperties = KinesisAnalyticsRuntime.getApplicationProperties();
			// Properties consumerProperties = applicationProperties.get("ConsumerConfigProperties");
			// Properties consumerProperties = applicationProperties.get("t1");

			Set<String> groups = applicationProperties.keySet();
			String debugMessage = "";

			for (String group : groups) {
				debugMessage += "[" + group + "]";
				Properties consumerProperties = applicationProperties.get(group);
				Set<Object> keys = consumerProperties.keySet();

				for (Object key : keys) {
					String sKey = key.toString();
					Object oValue = consumerProperties.get(key);
					String value = oValue.toString(); // consumerProperties.getProperty(sKey);
					debugMessage += "(" + key.toString() + "=" + value + "/" + oValue.toString() + ")";

					// InputStreamName
					if (sKey.equalsIgnoreCase("inputstreamname")) inputStreamName = value;
					if (sKey.equalsIgnoreCase("region")) region = value;
					if (sKey.equalsIgnoreCase("timestreamdbname")) databaseName = value;
					if (sKey.equalsIgnoreCase("timestreamtablename")) tableName = value;
					if (sKey.equalsIgnoreCase("metadata")) {
						metadata = value;
						LOG.error("Metadata used:" + value);
						System.out.println("INFO - Metadata used:" + value);
						JSONParser parser = new JSONParser();
						eventMetaData = (JSONObject) parser.parse(metadata);
						// throw new Exception("===(000) Debug Metadata - " + eventMetaData);
					}
				}
			}

			if (eventMetaData == null) throw new Exception("ERR-SJ-0000 EventMetaData is null - " + debugMessage);

			if (!parametersLoaded) throw new Exception("===(000) Debug Exception - " + debugMessage);


			// if (metadata == null)
			//	throw new Exception("===(001) No metadata configuration file specified - " + debugMessage);
			// else
			// throw new Exception("===(002) Unable to load metadata " + metadata);
		}

		if(region==null) region = parameter.get("Region", "us-east-1");
		if(databaseName==null) databaseName = parameter.get("TimestreamDbName", "kdaflink");
		if(tableName==null) tableName = parameter.get("TimestreamTableName", "kinesisdata");
		if(memoryStoreTTLHours==0L) memoryStoreTTLHours = Long.parseLong(parameter.get("MemoryStoreTTLHours", "24"));
		if(magneticStoreTTLDays==0L) magneticStoreTTLDays = Long.parseLong(parameter.get("MagneticStoreTTLDays", "7"));
		if(inputStreamName==null) inputStreamName = parameter.get("InputStreamName", "TimestreamTestStream");

		DataStream<EventMessage> mappedInput = createKinesisSource(env, parameter, region, inputStreamName);

		// EndpointOverride is optional. Learn more here: https://docs.aws.amazon.com/timestream/latest/developerguide/architecture.html#cells
		String endpointOverride = parameter.get("EndpointOverride", "");
		if (endpointOverride.isEmpty()) {
			endpointOverride = null;
		}

		TimestreamInitializer timestreamInitializer = new TimestreamInitializer(region, endpointOverride);
		timestreamInitializer.createDatabase(databaseName);
		timestreamInitializer.createTable(databaseName, tableName, memoryStoreTTLHours, magneticStoreTTLDays);

		String finalDatabaseName = databaseName;
		String finalTableName = tableName;

		TimestreamSink<EventMessage> sink = new TimestreamSink<>(
				(recordObject, context) -> {
					return TimestreamRecordConverter.convert(recordObject, jEventMetaData);
				},
				(List<Record> records) -> {
					LOG.debug("Preparing WriteRecordsRequest with {} records", records.size());
					System.out.println("Preparing WriteRecordsRequest with { " + records.size() + " } records");
					//timestream
					return WriteRecordsRequest.builder()
							.databaseName(finalDatabaseName)
							.tableName(finalTableName)
							.records(records)
							.build();
				},
				TimestreamSinkConfig.builder()
						.maxBatchSize(TIMESTREAM_RECORDS_IN_WRITERECORDREQUEST)
						.maxBufferedRequests(100 * TIMESTREAM_RECORDS_IN_WRITERECORDREQUEST)
						.maxInFlightRequests(MAX_CONCURRENT_WRITES_TO_TIMESTREAM)
						.maxTimeInBufferMS(15000)
						.emitSinkMetricsToCloudWatch(true)
						.writeClientConfig(TimestreamSinkConfig.WriteClientConfig.builder()
								.maxConcurrency(MAX_CONCURRENT_WRITES_TO_TIMESTREAM)
								.maxErrorRetry(10)
								.region(region)
								.requestTimeout(Duration.ofSeconds(20))
								.endpointOverride(endpointOverride)
								.build())
						.failureHandlerConfig(TimestreamSinkConfig.FailureHandlerConfig.builder()
								.failProcessingOnErrorDefault(true)
								.failProcessingOnRejectedRecordsException(true)
								.printFailedRequests(false)
								.build())
						.build()
		);
		mappedInput
				.sinkTo(sink)
				.disableChaining();
		env.execute("Timestream Metadata driven Flink Streaming Job V.1.07-25-2022");
	}
}
