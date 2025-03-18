package com.task05;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
		lambdaName = "api_handler",
		roleName = "api_handler-role",
		isPublishVersion = true,
		aliasName = "${lambdas_alias_name}",
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private static final String TABLE_NAME = System.getenv("TARGET_TABLE");
	private final DynamoDbClient dynamoDb = DynamoDbClient.create();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
		Map<String, Object> debugLogs = new HashMap<>();

		try {
			debugLogs.put("Step", "Parsing request body");

			// Validate request body
			if (request.getBody() == null || request.getBody().isEmpty()) {
				return response.withStatusCode(400).withBody("{\"error\": \"Request body is empty\"}");
			}

			// Parse and validate input
			Map<String, Object> requestBody = objectMapper.readValue(request.getBody(), Map.class);
			if (!requestBody.containsKey("principalId") || !requestBody.containsKey("content")) {
				return response.withStatusCode(400).withBody("{\"error\": \"Missing required fields\"}");
			}

			debugLogs.put("Step", "Generating UUID and timestamps");

			// Generate values
			String eventId = UUID.randomUUID().toString();
			String createdAt = Instant.now().toString();
			int principalId = (int) requestBody.get("principalId");
			Map<String, Object> content = objectMapper.convertValue(requestBody.get("content"), Map.class);

			// Prepare the DynamoDB item
			Map<String, AttributeValue> item = new HashMap<>();
			item.put("id", AttributeValue.builder().s(eventId).build());
			item.put("principalId", AttributeValue.builder().n(String.valueOf(principalId)).build());
			item.put("createdAt", AttributeValue.builder().s(createdAt).build());
			item.put("body", AttributeValue.builder().m(convertToDynamoDBMap(content)).build());

			debugLogs.put("Step", "Saving to DynamoDB");
			debugLogs.put("DynamoDB Item", item);

			// Save to DynamoDB
			PutItemRequest putItemRequest = PutItemRequest.builder()
					.tableName(TABLE_NAME)
					.item(item)
					.build();
			dynamoDb.putItem(putItemRequest);

			debugLogs.put("Step", "Data saved to DynamoDB");

			// Prepare response
			Map<String, Object> responseBody = new HashMap<>();
			responseBody.put("id", eventId);
			responseBody.put("principalId", principalId);
			responseBody.put("createdAt", createdAt);
			responseBody.put("body", content);

			response.setStatusCode(201);
			response.setBody(objectMapper.writeValueAsString(Map.of("event", responseBody)));

			debugLogs.put("Step", "Returning success response");
			return response;

		} catch (Exception e) {
			e.printStackTrace();
			debugLogs.put("Error", e.getMessage());
			return response.withStatusCode(500).withBody("{\"error\": \"Internal Server Error\", \"debug\": " + debugLogs.toString() + "}");
		}
	}

	private Map<String, AttributeValue> convertToDynamoDBMap(Map<String, Object> content) {
		Map<String, AttributeValue> dynamoDBMap = new HashMap<>();
		content.forEach((key, value) -> {
			if (value != null) {
				dynamoDBMap.put(key, AttributeValue.builder().s(value.toString()).build());
			}
		});
		return dynamoDBMap;
	}
}
