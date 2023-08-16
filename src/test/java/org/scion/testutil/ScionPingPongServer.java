package org.scion.testutil;

import org.scion.ScionDatagramSocket;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.*;

public class ScionPingPongServer {
    private final ScionDatagramSocket socket;
    private final List<String> listData = new ArrayList<>();
    private final Random random;

    public ScionPingPongServer(int port) throws SocketException {
        socket = new ScionDatagramSocket(port);
        random = new Random();
        listData.add("Pong 1");
        listData.add("Pong 22");
        listData.add("Pong 333");
    }

    public static void main(String[] args) {
        //int port = 13579;
        int port = 30255;

        try {
            ScionPingPongServer server = new ScionPingPongServer(port);
            server.service();
        } catch (SocketException ex) {
            System.out.println("Socket error: " + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("I/O error: " + ex.getMessage());
        }
    }

    private void service() throws IOException {
        while (true) {
            DatagramPacket request = new DatagramPacket(new byte[1], 1);
            socket.receive(request);

            String quote = getRandomPong();
            byte[] buffer = quote.getBytes();

            InetAddress clientAddress = request.getAddress();
            int clientPort = request.getPort();

            DatagramPacket response = new DatagramPacket(buffer, buffer.length, clientAddress, clientPort);
            socket.send(response);
        }
    }

    private String getRandomPong() {
        int randomIndex = random.nextInt(listData.size());
        return listData.get(randomIndex);
    }
}