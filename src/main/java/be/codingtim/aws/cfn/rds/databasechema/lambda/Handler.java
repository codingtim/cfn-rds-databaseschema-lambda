package be.codingtim.aws.cfn.rds.databasechema.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * https://docs.aws.amazon.com/lambda/latest/dg/java-handler.html
 */
@SuppressWarnings("unchecked")
public class Handler implements RequestHandler<Map<String, Object>, Void> {

    private final ObjectMapper objectMapper;

    public Handler() {
        this.objectMapper = new ObjectMapper();
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public Void handleRequest(Map<String, Object> event, Context context) {
        //https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/template-custom-resources.html
        //Example data:
//        {
//            "RequestType" : "Create",
//                "ResponseURL" : "http://pre-signed-S3-url-for-response",
//                "StackId" : "arn:aws:cloudformation:us-west-2:123456789012:stack/stack-name/guid",
//                "RequestId" : "unique id for this create request",
//                "ResourceType" : "Custom::TestResource",
//                "LogicalResourceId" : "MyTestResource",
//                "ResourceProperties" : {
//                    "Name" : "Value",
//                    "List" : [ "1", "2", "3" ]
//                 }
//        }
        LambdaLogger logger = context.getLogger();

        CloudFormationResponseSender cloudFormationResponseSender = new CloudFormationResponseSender(event, logger);

        Map<String, Object> properties = (Map<String, Object>) event.get("ResourceProperties");
        String secretArn = (String) properties.get("SecretArn");

        try {
            logger.log("Get secret: " + secretArn);
            final Map<String, String> secretAsMap = getSecretAsMap(secretArn);
            logger.log("Got secret: " + secretAsMap);
            cloudFormationResponseSender.success();
        } catch (IOException e) {
            cloudFormationResponseSender.failed();
        }
        return null;
    }

    private Map<String, String> getSecretAsMap(String secretArn) throws IOException {
        AWSSecretsManager client = AWSSecretsManagerClientBuilder.standard().build();

        GetSecretValueRequest getSecretValueRequest = new GetSecretValueRequest()
                .withSecretId(secretArn).withVersionStage("AWSCURRENT");

        GetSecretValueResult getSecretValueResult = client.getSecretValue(getSecretValueRequest);
        String secretString = getSecretValueResult.getSecretString();
        return objectMapper.readValue(secretString, Map.class);
    }

    private class CloudFormationResponseSender {
        private final LambdaLogger logger;
        private final Map<String, Object> response;
        private final String responseURL;

        //Example response:
//        {
//            "Status" : "SUCCESS",
//                "PhysicalResourceId" : "TestResource1",
//                "StackId" : "arn:aws:cloudformation:us-west-2:123456789012:stack/stack-name/guid",
//                "RequestId" : "unique id for this create request",
//                "LogicalResourceId" : "MyTestResource",
//                "Data" : {
//                    "OutputName1" : "Value1",
//                    "OutputName2" : "Value2",
//                }
//        }
        public CloudFormationResponseSender(Map<String, Object> event, LambdaLogger logger) {
            this.logger = logger;
            response = new HashMap<>();
            response.put("PhysicalResourceId", UUID.randomUUID().toString());
            response.put("StackId", event.get("StackId"));
            response.put("RequestId", event.get("RequestId"));
            response.put("LogicalResourceId", event.get("LogicalResourceId"));

            responseURL = (String) event.get("ResponseURL");
        }

        public void success() {
            sendResponse("SUCCESS");
        }

        public void failed() {
            sendResponse("FAILED");
        }

        private void sendResponse(String status) {
            try {
                response.put("Status", status);
                HttpClient httpClient = HttpClient.newBuilder().build();
                HttpRequest httpRequest = HttpRequest.newBuilder(new URI(responseURL))
                        .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(response)))
                        .build();
                HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                logger.log("Send status to cfn, response code: " + httpResponse.statusCode());
            } catch (URISyntaxException | IOException | InterruptedException e) {
                logger.log("Error sending response: " + e.getMessage());
            }
        }
    }
}