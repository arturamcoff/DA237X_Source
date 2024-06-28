/*
See comments in Main.java for explanation of the code.
 */

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;

public class ServerThread implements Runnable {
    private Socket socFromLambda;
    private final String FORWARD_IP = "IP OF ON-PREM PROXY SERVER";
    private final int FORWARD_PORT = 8080;

    private final String KEY = "PRE-SHARED SYMMETRIC ENCRYPTION KEY";
    private final String KEY_HMAC = "PRE-SHARED SYMMETRIC HMAC KEY";
    private static ArrayList<Integer> nonceList = new ArrayList<>();

    public ServerThread(Socket socFromLambda) {
        this.socFromLambda = socFromLambda;
    }

    @Override
    public void run() {

        try{
            byte[] buffer = new byte[64];
            socFromLambda.getInputStream().read(buffer);

            String decryptedRead = decryptMessageUsingKey(buffer, KEY);
            byte[] decryptedReadAsBytes = decryptedRead.getBytes(StandardCharsets.ISO_8859_1);
            byte[] messageReadWithNonce = splitArray(decryptedReadAsBytes, 0, 16);
            String messageReadAsStringWithNonce = new String(messageReadWithNonce, StandardCharsets.ISO_8859_1);
            byte[] hmacRead = splitArray(decryptedReadAsBytes, 16, 48);

            byte[] validateHMAC = generateHMAC(messageReadAsStringWithNonce, KEY_HMAC);
            if(!Arrays.equals(hmacRead, validateHMAC)){
                System.out.println("HMAC Error");
                return;
            }

            String messageWithoutNonce = messageReadAsStringWithNonce.substring(0,8);
            int nonce = Integer.parseInt(messageReadAsStringWithNonce.substring(8, 16));

            if(nonceList.contains(nonce)){
                System.out.println("Nonce Error");
                return;
            }else{
                nonceList.add(nonce);
            }

            String response = forwardMessageToLegacyServer(messageWithoutNonce); //8 bytes
            response = response + nonce; //16 bytes

            byte[] returnHmac = generateHMAC(response, KEY_HMAC); //32 bytes
            byte[] responeMessageAsBytes = response.getBytes(StandardCharsets.ISO_8859_1); //16 bytes
            byte[] responeMessageAndHMAC = combineByteArray(responeMessageAsBytes, returnHmac); //48 bytes
            String responeAndHMACAsString = new String(responeMessageAndHMAC, StandardCharsets.ISO_8859_1);
            byte[] encInput = encryptMessageUsingKey(responeAndHMACAsString, KEY);
            OutputStream out = socFromLambda.getOutputStream();
            out.write(encInput);
            out.flush();
            out.close();
            socFromLambda.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private String forwardMessageToLegacyServer(String message){
        try{
            Socket socketToLegacyServer = new Socket();
            socketToLegacyServer.connect(new InetSocketAddress(FORWARD_IP, FORWARD_PORT), 500);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socketToLegacyServer.getOutputStream()));
            BufferedReader in = new BufferedReader(new InputStreamReader(socketToLegacyServer.getInputStream()));

            out.write(message + "\n");
            out.flush();

            String returnMessage = in.readLine();
            out.close();
            in.close();
            return returnMessage;

        }catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String decryptMessageUsingKey(byte[] message, String key){
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

    public byte[] encryptMessageUsingKey(String message, String key){
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

    public byte[] generateHMAC(String message, String key){
        String messageAndKey = message + key;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(messageAndKey.getBytes());
            return hash;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] combineByteArray(byte[] arr1, byte[] arr2){
        byte[] newArray = new byte[arr1.length + arr2.length];
        for(int i = 0; i < arr1.length; i++){
            newArray[i] = arr1[i];
        }
        for(int i = arr1.length; i < (arr2.length+arr1.length); i++){
            newArray[i] = arr2[i - arr1.length];
        }
        return newArray;
    }

    public byte[] splitArray(byte[] arr, int start, int end){
        byte[] newArray = new byte[end - start];
        for(int i = start; i < end; i++){
            newArray[i - start] = arr[i];
        }
        return newArray;
    }
}
