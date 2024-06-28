/*
Written by Artur Amcoff.

Tester application for the reference system. It assumes that it can establish a direct connection
to the legacy server.

Can either test a single connection or n connections, where n is specified by the user.

If n, it will iterate the test n times, and then output the result for the individual tests
in comma separated format.

runSingle() is not updated accordingly, and will not yield a valid result. However, testN() is
used for the actual testing.

Compiled on the client, and hence no build system is used.
 */

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Main {

    public static final String IP = "IP OF LEGACY SERVER";
    public static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Press 1 to test single, 2 to test n, 3 for no percent");
        int choice = scanner.nextInt();

        if(choice == 1){
            runSingleTest(IP, PORT);
        } else if(choice == 2){
            System.out.println("Input n");
            int n = scanner.nextInt();
            testN(IP, PORT, n, true);
        }else if(choice == 3){
            System.out.println("Input n");
            int n = scanner.nextInt();
            testN(IP, PORT, n, false);
        }

    }

    public static void testN(String ip, int port, int n, boolean printPercent) throws IOException {
        int[] timesConnection = new int[n];
        int[] timesRTT = new int[n];

        for(int i = 0; i < n; i++){

            long startSetup = System.currentTimeMillis();
            Socket soc = new Socket(ip, port);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(soc.getOutputStream()));
            BufferedReader in = new BufferedReader(new InputStreamReader(soc.getInputStream()));
            long sendMessage = System.currentTimeMillis();
            out.write("Request from client\n");
            out.flush();

            String message = in.readLine();
            long endMessage = System.currentTimeMillis();
            if(!message.contains("ACK")){
                System.out.println("ACK FAILED");
                System.exit(2);
            }

            long end = System.currentTimeMillis();

            long connectionTime = end - startSetup;
            long RTT = endMessage - sendMessage;
            timesConnection[i] = (int) connectionTime;
            timesRTT[i] = (int) RTT;

            double nAsDouble = (double) n;
            double percent = (double) i / nAsDouble * 100;

            if(printPercent) {
                System.out.println("Progress: " + percent + "%");
            }
        }

        System.out.printf("Successfully ran %s tests. First line indicate connection time, second line indicate RTT.\n", n);
        System.out.println(printArrayCSV(timesConnection));
        System.out.println(printArrayCSV(timesRTT));

    }

    public static void runSingleTest(String ip, int port) throws IOException {

        long startSetup = System.currentTimeMillis();
        Socket soc = new Socket(ip, port);
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(soc.getOutputStream()));
        BufferedReader in = new BufferedReader(new InputStreamReader(soc.getInputStream()));
        long sendMessage = System.currentTimeMillis();
        out.write("Request from client\n");
        out.flush();

        String message = in.readLine();
        long end = System.currentTimeMillis();
        System.out.println("Message from server: " + message);

        System.out.println("Total time incl setup: " + (end-startSetup) + " ms");
        System.out.println("Message RTT: " + (end-sendMessage) + " ms");
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