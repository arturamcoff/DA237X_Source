/*
Created by Artur Amcoff

Uses the AWS SDK for Java 2. Documentation for used components can be found at:
https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/ec2/Ec2Client.html

Encryption using javax.crypto library.

Application to test the second implementation. For each test, the application will check if there
exist a connection already open. If no, it will request to open a route though the Network ACL
and local on-prem firewall.

Once open, it will communicate directly with the on-prem proxy. HMAC, Nonce is generated using pre-shared
keys, and the data is encryption also with a pre-shared key.

On return, the application will decrypt the data, and check HMAC validity. If valid, the application will
log the times.

If connection is interrupted during the test, the application will log that, and re-attempt.

 */

package dev.exjobb;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Random;
import java.util.Scanner;

public class App {

    public static final String PAYLOAD = "55555555";
    public static final String HMAC_KEY = "SYMMERIC HMAC KEY";
    public static final String KEY = "SYMMETRIC ENCRYPTION KEY";
    public static final String REMOTE_SERVER = "ON-PREM PROXY IP";
    public static String KEEP_CONNECTION_OPEN_TIME = "5000"; //Default value
    public static final int REMOTE_PORT = 8080;



    public static void main( String[] args ) throws InterruptedException {
        System.out.println("Enter timeout in milliseconds");
        Scanner scanner = new Scanner(System.in);
        KEEP_CONNECTION_OPEN_TIME = scanner.nextLine();

        System.out.println("1 for single test, 2 for n texts, 3 for n tests without percent");
        int choice = scanner.nextInt();
        if (choice == 1) {
            sendN(1, true);
        } else if (choice == 2) {
            System.out.println("Enter number of tests");
            int n = scanner.nextInt();
            sendN(n, true);
        } else if (choice == 3) {
            System.out.println("Enter number of tests");
            int n = scanner.nextInt();
            sendN(n, false);
        }
        System.exit(0);
    }

    public static void sendN(int n, boolean printPercent) throws InterruptedException {
        int[] timeFullConnection = new int[n];
        int[] timeRTT = new int[n];
        int[] attemptSufferedLostConnection = new int[n];
        int numberOfInterruptedConnections = 0;

        for(int i = 0; i < n; i++){
            int[] status = sendRequest();
            if(status[0] != 1) {
                System.out.println("Returned status: " + status[0] + " at test " + i);
                attemptSufferedLostConnection[i]++;
                i--;
                numberOfInterruptedConnections++;
                Thread.sleep(Long.parseLong(KEEP_CONNECTION_OPEN_TIME));

            }else if (status[0] == 1){
                timeFullConnection[i] = status[1];
                timeRTT[i] = status[2];
                if(printPercent) {
                    double nAsDouble = (double) n;
                    double percent = ((i+1) / nAsDouble) * 100;
                    System.out.println("Test " + i + " completed, " + percent + "%");

                }
            }
        }

        System.out.println("Number of interrupted connections: " + numberOfInterruptedConnections);
        System.out.printf("Successfully ran %s tests. First line indicate connection time, second line indicate RTT.\n", n);
        System.out.println(printArrayCSV(timeFullConnection));
        System.out.println(printArrayCSV(timeRTT));
        System.out.println(printArrayCSV(attemptSufferedLostConnection));
    }

    public static int[] sendRequest() throws InterruptedException {
        int[] returnValues = {0,0,0}; //First is status, second is connection time, third is RTT
        long startTime = System.currentTimeMillis();

        byte[] encInput = prepareMessageToRemoteServer();

        boolean connectionEstablished = establishConnection();
        if(!connectionEstablished){
            returnValues[0] = 2;
            return returnValues;
        }

        byte[] buffer = new byte[80];
        MakeRequest request = new MakeRequest(returnValues, buffer, encInput);
        Thread thread = new Thread(request);
        thread.start();
        thread.join(100);
        if(returnValues[2] == 0){
            thread.interrupt();
            returnValues[0] = 2;
            return returnValues;
        }

        checkResponse(buffer, returnValues);
        long endTime = System.currentTimeMillis();
        int totalMessageTime = (int) (endTime - startTime);

        returnValues[1] = totalMessageTime;
        return returnValues;

    }

    public static byte[] prepareMessageToRemoteServer(){
        String generateNonce = generateNonce();
        String payloadWithNonce = PAYLOAD + generateNonce;
        byte[] HMAC = generateHMAC(payloadWithNonce, HMAC_KEY);
        byte[] messageAsBytes = payloadWithNonce.getBytes(StandardCharsets.ISO_8859_1);
        byte[] messageAndHMAC = combineByteArray(messageAsBytes, HMAC);
        String messageAndHMACAsString = new String(messageAndHMAC, StandardCharsets.ISO_8859_1);
        byte[] encInput = encryptMessageWithKey(messageAndHMACAsString, KEY);
        return encInput;
    }

    public static String checkResponse(byte[] buffer, int[] returnValues){
        byte[] newBuffer;

        //Needed because uncertainty if return is 64 or 80 bytes long.
        if(buffer[79] == 0){
            newBuffer = splitArray(buffer, 0, 64);
        }else{
            newBuffer = buffer;
        }
        String decrypt = decryptMessageWithKey(newBuffer, KEY);
        byte[] decryptAsByte = decrypt.getBytes(StandardCharsets.ISO_8859_1);

        byte[] responseMessage = splitArray(decryptAsByte, 0, 16);
        byte[] responseHMAC = splitArray(decryptAsByte, 16, 48);
        String responseMessageString = new String(responseMessage, StandardCharsets.ISO_8859_1);
        byte[] validateHMAC = generateHMAC(responseMessageString, HMAC_KEY);

        if(responseMessageString.contains("ACK")) {
            returnValues[0] = 1;
        }else {
            returnValues[0] = 2;
        }

        if(validateHMAC.equals(responseHMAC)){
            returnValues[0] = 3;
        }
        return responseMessageString;
    }

    public static boolean establishConnection(){
        try{
            boolean isReachable = InetAddress.getByName(REMOTE_SERVER).isReachable(100);
            if (isReachable == false){
                requestRouteOpen(KEEP_CONNECTION_OPEN_TIME);
                for(int i = 0; i < 50; i++){
                    if (InetAddress.getByName(REMOTE_SERVER).isReachable(100)){
                        break;
                    }

                    if(i == 49){
                        return false;
                    }

                }
            }
        }catch(UnknownHostException e){
            System.out.println("Cannot reach ip");
        }catch(IOException e){
            System.out.println("IO Exception when reaching IP");
        }
        return true;
    }

    public static void requestRouteOpen(String time) {
        InvokeLambdaThread thread = new InvokeLambdaThread(time);
        Thread t = new Thread(thread);
        t.start();
    }

    public static byte[] encryptMessageWithKey(String message, String key){
        try{
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            byte[] enc = cipher.doFinal(message.getBytes(StandardCharsets.ISO_8859_1));
            return enc;
        }catch (Exception e)   {
            return null;
        }
    }

    public static String decryptMessageWithKey(byte[] message, String key){
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
            String dec = new String(cipher.doFinal(message), StandardCharsets.ISO_8859_1);
            return dec;
        }catch (Exception e){
            return null;
        }
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
