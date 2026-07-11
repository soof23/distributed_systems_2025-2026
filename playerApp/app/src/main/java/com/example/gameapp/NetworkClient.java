package com.example.gameapp;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class NetworkClient {
    private static NetworkClient instance;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private static final String MASTER_HOST = "10.26.27.127";   // 10.0.2.2 IP για τον Emulator
    private static final int MASTER_PORT = 5055;

    private NetworkClient() {}

    public static synchronized NetworkClient getInstance() {
        if (instance == null) {
            instance = new NetworkClient();
        }
        return instance;
    }

    public void connect() throws IOException {
        if (socket == null || socket.isClosed()) {
            socket = new Socket(MASTER_HOST, MASTER_PORT);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
        }
    }

    public synchronized Object sendRequest(String command, Object data) {
        try {
            connect();
            out.writeObject(command);
            out.writeObject(data);
            out.flush();
            out.reset();
            return in.readObject();
        } catch (Exception e) {
            e.printStackTrace();
            disconnect();
            return null;
        }
    }

    public void disconnect() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            out = null;
            in = null;
            socket = null;
        }
    }
}