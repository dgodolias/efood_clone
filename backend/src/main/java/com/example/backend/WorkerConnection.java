package com.example.backend;

import java.io.*;
import java.net.*;

public class WorkerConnection {
    private String host;
    private int port;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public WorkerConnection(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        connect();
    }

    private void connect() throws IOException {
        socket = new Socket(host, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    public String sendRequest(String request) throws IOException {
        try {
            request = request.replace("\n", " ").replace("\r", "");
            out.println(request);
            return in.readLine();
        } catch (IOException e) {
            connect(); // Reconnect on failure
            request = request.replace("\n", " ").replace("\r", "");
            out.println(request);
            return in.readLine();
        }
    }

    public void close() throws IOException {
        if (socket != null) socket.close();
        if (out != null) out.close();
        if (in != null) in.close();
    }

    public int getPort() {
        return port;
    }
    
    public String getHost() {
        return host;
    }
}