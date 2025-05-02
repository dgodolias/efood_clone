package com.example.backend;

import java.io.*;
import java.net.*;

public class WorkerConnection {
    private String host;
    private int port;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Master master;

    public WorkerConnection(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        connect();
    }

    public WorkerConnection(String host, int port, Master master) throws IOException, ClassNotFoundException {
        this.host = host;
        this.port = port;
        this.master = master;
        connect();
        try {
            CommunicationClasses.WorkerRequest pingRequest = new CommunicationClasses.WorkerRequest("PING", "");
            CommunicationClasses.WorkerResponse response = sendRequest(pingRequest);
            if (response.getResult().equals("PONG") && master != null) master.markWorkerAsInitialized(this);
        } catch (IOException e) {
            System.err.println("Failed to verify worker initialization: " + e.getMessage());
        }
    }

    private void connect() throws IOException {
        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
    }

    public CommunicationClasses.WorkerResponse sendRequest(CommunicationClasses.WorkerRequest request) throws IOException, ClassNotFoundException {
        try {
            out.writeObject(request);
            out.flush();
            return (CommunicationClasses.WorkerResponse) in.readObject();
        } catch (IOException e) {
            connect();
            out.writeObject(request);
            out.flush();
            try {
                return (CommunicationClasses.WorkerResponse) in.readObject();
            } catch (ClassNotFoundException cnfe) {
                throw cnfe;
            }
        } catch (ClassNotFoundException e) {
            throw new IOException("Class not found: " + e.getMessage(), e);
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