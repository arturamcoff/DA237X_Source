/*
Lambda function code.
Created by Artur Amcoff.

Upon invocation, a message is received. The message is encrypted with AES using a pre-shared key.
The encrypted message is then sent to a server, which proxies the legacy server, on a specified IP and port.
The server responds with a message, which is decrypted and returned to the invoker.

Uses AES encryption with ECB mode and PKCS5 padding using javax.crypto package.

For simplicity, the IP address and port are hardcoded, as well as the encryption key.
Message-length is also hard-coded.

Application is compiled with Maven to create a runnable .JAR file with all dependencies included.
 */

package dev.exjobb;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ForwardMessage {

    public final String IP = "IP OF ON-PREM PROXY SERVER";
    public final int PORT = 8080;

    public final int TIMEOUT = 500;
    public final String KEY = "SYMMETRIC ENCRYPTION KEY";


    public String handleRequest(String inputFromBusSrv){
        String removeJson = inputFromBusSrv.replace("\"", "");

        byte[] inputAsByte = Base64.getDecoder().decode(removeJson);
        String input = new String(inputAsByte, StandardCharsets.ISO_8859_1);

        String respone = "";

        try {
            byte[] encInput = encryptMessageWithKey(input, KEY); //48 bytes

            Socket soc = new Socket();
            soc.connect(new InetSocketAddress(IP, PORT), TIMEOUT);
            soc.setSoTimeout(TIMEOUT);
            OutputStream out = soc.getOutputStream();
            out.write(encInput);
            out.flush();

            byte[] buffer = new byte[80];
            int read = soc.getInputStream().read(buffer);

            byte[] newBuffer;

            //Needed because cipher length appear to vary between either 64 or 80 bytes
            if(buffer[79] == 0){
                newBuffer = splitArray(buffer, 0, 64);
            }else{
                newBuffer = buffer;
            }

            String decrypt = decryptMessageWithKey(newBuffer, KEY);
            byte[] decryptAsByte = decrypt.getBytes(StandardCharsets.ISO_8859_1);
            String b64 = Base64.getEncoder().encodeToString(decryptAsByte);

            out.close();
            soc.close();
            return b64;

        }catch (SocketTimeoutException e){
            respone = "Timeout";
        } catch (UnknownHostException e) {
            respone = "UnknownHostException";
        } catch (IOException e) {
            respone = "Error";
        }
        return null;
    }

    public byte[] encryptMessageWithKey(String message, String key){
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

    public String decryptMessageWithKey(byte[] message, String key){
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

