/*
Written by Artur Amcoff

This program will listen on port 9090 for incoming connection (from the controller Lambda function).
The incoming connection will request to open firewall of a given amount of time. Open firewall using .sh script.
Then wait given time, before closing with other .sh script.

MUST BE RUN AS SUDO!

 */

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class App
{
    public static void main( String[] args ) throws IOException, InterruptedException {
        ServerSocket serverSocket = new ServerSocket(9090);
        while(true) {
            Socket soc = serverSocket.accept();
            BufferedReader in = new BufferedReader(new InputStreamReader(soc.getInputStream()));
            String message = in.readLine();

            int timer = Integer.parseInt(message);
            triggerOpen();
            Thread.sleep(timer);
            triggerClose();
            soc.close();
        }

    }

        public static void triggerOpen() throws IOException {
            ProcessBuilder pb = new ProcessBuilder("PATH TO OPEN SCRIPT");
            Process p = pb.start();
        }

        public static void triggerClose() throws IOException {
            ProcessBuilder pb = new ProcessBuilder("PATH TO CLOSE SCRIPT");
            Process p = pb.start();
        }
}
