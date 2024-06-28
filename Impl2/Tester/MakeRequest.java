/*
See App.java for description and comments
 */

package dev.exjobb;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class MakeRequest implements Runnable{
    int[] returnValues;
    byte[] buffer;
    byte[] encInput;

    public MakeRequest(int[] returnValues, byte[] buffer, byte[] encInput){
        this.returnValues = returnValues;
        this.buffer = buffer;
        this.encInput = encInput;
    }

    @Override
    public void run() {
        try{
            Socket soc = new Socket();
            soc.connect(new InetSocketAddress(App.REMOTE_SERVER, App.REMOTE_PORT), 100);

            long sendTime = System.currentTimeMillis();
            soc.getOutputStream().write(encInput);
            int read = soc.getInputStream().read(buffer);
            long receiveTime = System.currentTimeMillis();
            returnValues[2] = (int) (receiveTime-sendTime);
        }catch (IOException e){
            System.out.println("Connection timeout during active connection");
            returnValues[0] = 2;
        }

    }

}
