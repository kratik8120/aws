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
		try {
			// Parse the request body
			Map<String, Object> requestBody = objectMapper.readValue(request.getBody(), Map.class);

			// Generate UUID and timestamp
			String eventId = UUID.randomUUID().toString();
			String createdAt = Instant.now().toString();
			int principalId = (int) requestBody.get("principalId");
			Map<String, String> content = (Map<String, String>) requestBody.get("content");

			// Prepare the DynamoDB item
			Map<String, AttributeValue> item = new HashMap<>();
			item.put("id", AttributeValue.builder().s(eventId).build());
			item.put("principalId", AttributeValue.builder().n(String.valueOf(principalId)).build());
			item.put("createdAt", AttributeValue.builder().s(createdAt).build());
			item.put("body", AttributeValue.builder().s(objectMapper.writeValueAsString(content)).build());

			// Save to DynamoDB
			PutItemRequest putItemRequest = PutItemRequest.builder()
					.tableName(TABLE_NAME)
					.item(item)
					.build();
			dynamoDb.putItem(putItemRequest);

			// Prepare response
			Map<String, Object> responseBody = new HashMap<>();
			responseBody.put("id", eventId);
			responseBody.put("principalId", principalId);
			responseBody.put("createdAt", createdAt);
			responseBody.put("body", content);

			return new APIGatewayProxyResponseEvent()
					.withStatusCode(201)
					.withBody(objectMapper.writeValueAsString(Map.of("event", responseBody)));

		} catch (Exception e) {
			e.printStackTrace();
			return new APIGatewayProxyResponseEvent()
					.withStatusCode(500)
					.withBody("{\"error\": \"Internal Server Error\"}");
		}
	}
}
