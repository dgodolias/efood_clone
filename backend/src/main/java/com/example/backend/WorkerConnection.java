package com.example.backend;

import java.io.*;
import java.net.*;

public class WorkerConnection {
    private String host;
    private int port;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Master master; // Reference to the Master for initialization notification

    public WorkerConnection(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        connect();
    }
    
    // Alternative constructor that includes Master reference
    public WorkerConnection(String host, int port, Master master) throws IOException {
        this.host = host;
        this.port = port;
        this.master = master;
        connect();
        
        // Attempt to ping the worker to verify it's ready
        try {
            String response = sendRequest("PING");
            if (response != null && response.equals("PONG")) {
                // Worker is responsive, notify the master it's initialized
                if (master != null) {
                    master.markWorkerAsInitialized(this);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to verify worker initialization: " + e.getMessage());
            // Don't mark as initialized yet
        }
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