/*
See App.java for description and comments
 */

package dev.exjobb;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.TooManyRequestsException;

import java.nio.charset.StandardCharsets;

public class InvokeLambdaThread implements Runnable{

    public final Region REGION = Region.EU_NORTH_1;
    public final String FUNCTION_NAME = "AWS LAMBDA FUNCTION NAME";
    public String TIME;

    public InvokeLambdaThread(String time) {
        this.TIME = time;
    }
    @Override
    public void run() {

        String messageInSendableFormat = App.prepareTransmissionToLambda(TIME.getBytes(StandardCharsets.ISO_8859_1));

        LambdaClient lambda = LambdaClient.builder()
                .region(REGION)
                .build();

        SdkBytes bytes = SdkBytes.fromString(messageInSendableFormat, StandardCharsets.ISO_8859_1);

        InvokeRequest request = InvokeRequest.builder()
                .functionName(FUNCTION_NAME)
                .payload(bytes)
                .build();

        try{
            InvokeResponse response = lambda.invoke(request);
            String responseAsString = response.payload().asString(StandardCharsets.UTF_8);
        }catch (TooManyRequestsException e){
            System.out.println("Too many requests");
            try {
                Thread.sleep(Long.parseLong(TIME));
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
