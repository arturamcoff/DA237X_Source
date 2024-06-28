/*
Proxy for legacy server.
Created by Artur Amcoff using Java Sockets, and Javax.Crypto library.

Running a ServerSocket on specified port. Incoming request from Lambda is decrypted, validated, and forwarded
to the legacy server without encryption. The response is then used to generate new HMAC
which is encrypted together with the response data and returned to the Lambda mediator.

Uses AES encryption with ECB mode and PKCS5 padding. Pre-shared key is hardcoded. Uses javax.crypto library.

The legacy server should ONLY be allowed to communicate with the machine running this software,
and have NO other connections.

Compiled and run locally on the machine, hence, no build system is used.
 */

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {

    public static final int PORT = 8080;
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);

        while (true) {
            Socket soc = serverSocket.accept();
            ServerThread th = new ServerThread(soc);
            Thread t = new Thread(th);
            t.start();
        }
    }
}