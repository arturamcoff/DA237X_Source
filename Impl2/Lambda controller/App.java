/*
Lambda function created by Artur Amcoff

AWS SDK documentation:
https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/ec2/Ec2Client.html

This Lambda function ensures that a connection is temporarly opened between the tester application
and the on-prem proxy. It sends a command to the on-prem server to open a port for a specific amount of time,
as well as modify the network access control list in AWS. After the specified time have passet, it closes again.

This function is built with Maven into a JAR file, including dependencies, and uploaded to AWS.
 */

package dev.prolog;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class App
{
    public Socket soc;
    public final String onPremServerIP = "ON-PREM SERVER IP";
    public final int windowsServerPort = 9090;

    public String handleRequest(String inputFromBusSrv) throws InterruptedException {
        String removeJson = inputFromBusSrv.replace("\"", "");

        byte[] inputAsByte = Base64.getDecoder().decode(removeJson);
        int timeToKeepConnectionOpen = Integer.parseInt(new String(inputAsByte, StandardCharsets.ISO_8859_1));

        configureConnection();
        triggerOnPremFirewall(timeToKeepConnectionOpen);
        closeConnection();
        openUsingNACK(98);
        Thread.sleep(timeToKeepConnectionOpen);
        closeUsingNACK(98);
        return "Success!";
    }


    public void configureConnection(){
        try {
            this.soc = new Socket(onPremServerIP, windowsServerPort);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void closeConnection(){
        try {
            this.soc.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void triggerOnPremFirewall(int timer) {
        try {
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(this.soc.getOutputStream()));
            String message = String.valueOf(timer);
            out.write(message + "\n");
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void openUsingNACK(int ruleNumber){
        Region region = Region.EU_NORTH_1;
        Ec2Client ec2Client = Ec2Client.builder()
                .region(region)
                .build();
        PortRange range = PortRange.builder()
                .from(8080)
                .to(8080)
                .build();

        CreateNetworkAclEntryRequest create = CreateNetworkAclEntryRequest.builder()
                .networkAclId("NETWORK ACCESS CONTROL LIST ID")
                .ruleNumber(ruleNumber)
                .portRange(range)
                .cidrBlock("ON-PREM IP ADDRESS")
                .egress(true)
                .protocol("-1")
                .ruleAction(RuleAction.ALLOW)
                .build();

        CreateNetworkAclEntryRequest create2 = CreateNetworkAclEntryRequest.builder()
                .networkAclId("NETWORK ACCESS CONTROL LIST ID")
                .portRange(range)
                .cidrBlock("ON-PREM IP ADDRESS")
                .egress(false)
                .protocol("-1")
                .ruleAction(RuleAction.ALLOW)
                .ruleNumber(ruleNumber)
                .build();

        CreateNetworkAclEntryResponse response = ec2Client.createNetworkAclEntry(create);
        CreateNetworkAclEntryResponse response2 = ec2Client.createNetworkAclEntry(create2);
    }

    public void closeUsingNACK(int ruleNumber){
        Region region = Region.EU_NORTH_1;
        Ec2Client ec2Client = Ec2Client.builder()
                .region(region)
                .build();

        DeleteNetworkAclEntryRequest delete = DeleteNetworkAclEntryRequest.builder()
                .networkAclId("NETWORK ACCESS CONTROL LIST ID")
                .ruleNumber(ruleNumber)
                .egress(true)
                .build();

        DeleteNetworkAclEntryRequest delete2 = DeleteNetworkAclEntryRequest.builder()
                .networkAclId("NETWORK ACCESS CONTROL LIST ID")
                .ruleNumber(ruleNumber)
                .egress(false)
                .build();

        DeleteNetworkAclEntryResponse response = ec2Client.deleteNetworkAclEntry(delete);
        DeleteNetworkAclEntryResponse response2 = ec2Client.deleteNetworkAclEntry(delete2);
        System.out.println("Success!");

    }

}
