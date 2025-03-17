package com.task04;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.eventsources.SqsTriggerEventSource;
import com.syndicate.deployment.annotations.eventsources.SnsEventSource;

import java.util.HashMap;
import java.util.Map;

@LambdaHandler(
    lambdaName = "sqs_handler",
	roleName = "sqs_handler-role",
	isPublishVersion = true,
	aliasName = "learn",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@SnsEventSource(
		targetTopic = "lambda_topic"
)
@SqsTriggerEventSource(
		targetQueue = "async_queue",
		batchSize = 10
)
public class SqsHandler implements RequestHandler<Object, Map<String, Object>> {

	public Map<String, Object> handleRequest(Object request, Context context) {

		context.getLogger().log("Received event: " + request.toString() + "\n");
		Map<String, Object> resultMap = new HashMap<String, Object>();
		resultMap.put("statusCode", 200);
		resultMap.put("body", "Processed SQS Messages Successfully");
		return resultMap;
	}
}
