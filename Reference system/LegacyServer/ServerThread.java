/*
Written by Artur Amcoff.
This application is representing the legacy server. It listens on port 8080 and accepts incoming connections.

The server does not contain any business logic, but rather just echoes the incoming message back to the client,
with a part of the string changed to "ACK\n" to mark that the message was actually at this server.

The server is multithreaded, meaning that it can handle multiple clients simultaneously.

Compiled locally at the server, meaning no build system is used.
 */

import java.io.*;
import java.net.Socket;

public class ServerThread  implements Runnable{
    Socket soc;

    public ServerThread(Socket soc) {
        this.soc = soc;
    }

    @Override
    public void run() {
        try {
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(soc.getOutputStream()));
            BufferedReader in = new BufferedReader(new InputStreamReader(soc.getInputStream()));
            String message = in.readLine();
            String respone = message.substring(0,5) + "ACK\n";
            out.write(respone);
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
