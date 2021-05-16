package be.codingtim.aws.cfn.rds.databasechema.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
public class Handler implements RequestHandler<Map<String, Object>, Void> {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

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
        // log execution details
        logger.log("ENVIRONMENT VARIABLES: " + gson.toJson(System.getenv()));
        logger.log("CONTEXT: " + gson.toJson(context));
        // process event
        logger.log("EVENT: " + gson.toJson(event));
        logger.log("EVENT TYPE: " + event.getClass().toString());
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
        Map<String, Object> response = new HashMap<>();
        response.put("Status", "SUCCESS");
        response.put("PhysicalResourceId", UUID.randomUUID().toString());
        response.put("StackId", event.get("StackId"));
        response.put("RequestId", event.get("RequestId"));
        response.put("LogicalResourceId", event.get("LogicalResourceId"));

        String responseURL = (String) event.get("ResponseURL");
        try {
            HttpClient httpClient = HttpClient.newBuilder().build();
            HttpRequest httpRequest = HttpRequest.newBuilder(new URI(responseURL))
                    .PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(response)))
                    .build();
            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            logger.log("Response code: " + httpResponse.statusCode());
        } catch (URISyntaxException | IOException | InterruptedException e) {
            logger.log("Error sending response: " + e.getMessage());
        }
        return null;
    }

}