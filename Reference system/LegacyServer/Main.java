/*
Written by Artur Amcoff.
This application is representing the legacy server. It listens on port 8080 and accepts incoming connections.

The server does not contain any business logic, but rather just echoes the incoming message back to the client,
with a part of the string changed to "ACK\n" to mark that the message was actually at this server.

The server is multithreaded, meaning that it can handle multiple clients simultaneously.

Compiled locally at the server, meaning no build system is used.
 */

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
    public static void main(String[] args) throws IOException {

        ServerSocket soc = new ServerSocket(8080);
        System.out.println("Server started");
        while (true) {
            Socket soc_con = soc.accept();
            ServerThread th = new ServerThread(soc_con);
            Thread t = new Thread(th);
            t.start();
        }
    }
}