/*
Test utility for Implementation 1.
Created by Artur Amcoff using the AWS SDK for Java version 2 and javax.crypto library.
https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html

Documentation of how to invoke a Lambda function using the AWS SDK for Java version 2:
https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-lambda.html

Utility can either run a single test or test n times. If n is chosen, the utility will output the connection time
and the round trip time for each test in a comma-separated list. The utility will also output the progress of the tests.

Note that the payload corresponds to a mock-value of multiple "5". This should not affect the performance.

Application is compiled with Maven to create a runnable .JAR file with all dependencies included.
 */

package dev.prolog;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;
import java.util.Scanner;
public class App
{
    public static String messageReturnValues;
    public static final String FUNCTION_NAME = "AWS LAMBDA FUNCTION NAME";
    public static final Region REGION = Region.EU_NORTH_1;
    public static final String PAYLOAD = "55555555";
    public static final String HMAC_KEY = "PRE-SHARED HMAC KEY";

    public static void main( String[] args ) {

        Scanner scanner = new Scanner(System.in);

        System.out.println("Press 1 to test single, 2 to test n, 3 to test n without percent, or 4 with simulator");
        int choice = scanner.nextInt();

        if(choice == 1){
            runSingleTest();

        } else if(choice == 2) {
            System.out.println("Input n");
            int n = scanner.nextInt();
            testN(n, true);

        }else if(choice == 3){
            System.out.println("Input n");
            int n = scanner.nextInt();
            testN(n, false);

        }
    }

    public static void runSingleTest(){
        int[] returnValues = executeRequest();
        System.out.println("Status of 1 is success, 2 or 3 is failed:");
        System.out.println("Status: " + returnValues[0]);
        System.out.println("Message received from server: " + messageReturnValues);
        System.out.println("Connection time: " + returnValues[1]);
        System.out.println("RTT: " + returnValues[2]);
    }
    public static int[] executeRequest(){
        int[] returnValues = {0,0,0}; //First is status, second is connection time, third is RTT

        long startSetup = System.currentTimeMillis();

        String generateNonce = generateNonce();
        String payloadWithNonce = PAYLOAD + generateNonce;
        byte[] HMAC = generateHMAC(payloadWithNonce, HMAC_KEY); //32 byte HMAC
        byte[] messageAsBytes = payloadWithNonce.getBytes(StandardCharsets.ISO_8859_1); //16 byte for simplicity
        byte[] messageAndHMAC = combineByteArray(messageAsBytes, HMAC); //48 byte
        String messageInSendableFormat = prepareTransmissionToLambda(messageAndHMAC);

        LambdaClient lambda = LambdaClient.builder()
                .region(REGION)
                .build();

        SdkBytes bytes = SdkBytes.fromString(messageInSendableFormat, StandardCharsets.ISO_8859_1);

        InvokeRequest request = InvokeRequest.builder()
                .functionName(FUNCTION_NAME)
                .payload(bytes)
                .build();

        long sendTime = System.currentTimeMillis();
        InvokeResponse response = lambda.invoke(request);
        long receivedTime = System.currentTimeMillis();


        byte[] decodedResponeBytes = decodeDataFromLambda(response.payload().asString(StandardCharsets.ISO_8859_1));
        byte[] responseMessage = splitArray(decodedResponeBytes, 0, 16);
        byte[] responseHMAC = splitArray(decodedResponeBytes, 16, 48);
        String responseMessageString = new String(responseMessage, StandardCharsets.ISO_8859_1);
        byte[] validateHMAC = generateHMAC(responseMessageString, HMAC_KEY);

        if(responseMessageString.contains("ACK")) {
            returnValues[0] = 1;
        }else {
            returnValues[0] = 2;
        }



        if(!Arrays.equals(responseHMAC, validateHMAC)){
            returnValues[0] = 3;
        }

        long endTime = System.currentTimeMillis();
        returnValues[1] = (int) (endTime - startSetup);
        returnValues[2] = (int) (receivedTime - sendTime);

        messageReturnValues = responseMessageString;
        return returnValues;
    }



    public static void testN(int n, boolean printPercent){

        int[] timesConnection = new int[n];
        int[] timesRTT = new int[n];

        for(int i = 0; i < n; i++) {
            int[] returnValues = executeRequest();
            if(returnValues[0] != 1){
                System.out.println("Failed test");
                System.exit(1);
            }

            timesConnection[i] = returnValues[1];
            timesRTT[i] = returnValues[2];

            if(printPercent) {
                double nAsDouble = (double) n;
                double percent = (double) i / nAsDouble * 100;
                System.out.println("Progress: " + percent + "%");
            }
        }
        System.out.printf("Successfully ran %s tests. First line indicate connection time, second line indicate RTT.\n", n);
        System.out.println(printArrayCSV(timesConnection));
        System.out.println(printArrayCSV(timesRTT));
    }

    public static String generateNonce(){
        String nonce = "00000000";
        Random random = new Random();

        for(int i = 0; i < nonce.length(); i++){
            int randomNum = random.nextInt(9);
            if(randomNum == 0){
                randomNum = 1;
            }
            nonce = nonce.substring(0, i) + randomNum + nonce.substring(i + 1);
        }
        return nonce;
    }

    public static byte[] generateHMAC(String message, String key){
        String messageAndKey = message + key;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(messageAndKey.getBytes());
            return hash;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] combineByteArray(byte[] arr1, byte[] arr2){
        byte[] newArray = new byte[arr1.length + arr2.length];
        for(int i = 0; i < arr1.length; i++){
            newArray[i] = arr1[i];
        }
        for(int i = arr1.length; i < (arr2.length+arr1.length); i++){
            newArray[i] = arr2[i - arr1.length];
        }
        return newArray;
    }

    public static byte[] splitArray(byte[] arr, int start, int end){
        byte[] newArray = new byte[end - start];
        for(int i = start; i < end; i++){
            newArray[i - start] = arr[i];
        }
        return newArray;
    }

    public static byte[] decodeDataFromLambda(String payloadAsString){
        String removeJsonFormatting = payloadAsString.replace("\"", "");
        byte[] decodedData = Base64.getDecoder().decode(removeJsonFormatting);
        return decodedData;
    }
    public static String prepareTransmissionToLambda(byte[] message){
        String messageAndHMACAsBase64String = Base64.getEncoder().encodeToString(message);
        String json = String.format("\"%s\"", messageAndHMACAsBase64String);
        return json;
    }

    public static String printArrayCSV(int[] arr){
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < arr.length; i++){
            sb.append(arr[i]);
            sb.append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

}
