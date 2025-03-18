package com.example.backend;

import java.io.*;
import java.net.*;
import java.util.*;

public class Master {
    private static final int PORT = 8080;
    private List<WorkerConnection> workers;

    public Master(String[] workerArgs) {
        workers = new ArrayList<>();
        if (workerArgs.length == 0) {
            System.out.println("Warning: No workers specified. Master will run but cannot process requests requiring workers.");
        } else {
            for (String arg : workerArgs) {
                String[] parts = arg.split(":");
                try {
                    workers.add(new WorkerConnection(parts[0], Integer.parseInt(parts[1])));
                    System.out.println("Connected to worker at " + arg);
                } catch (IOException e) {
                    System.err.println("Failed to connect to worker at " + arg + ": " + e.getMessage());
                }
            }
        }
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Master Server running on port " + PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected: " + socket.getInetAddress());
                new MasterThread(socket, workers).start();
            }
        } catch (IOException e) {
            System.err.println("Master server failed: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        Master master = new Master(args);
        master.start();
    }
}

class MasterThread extends Thread {
    private Socket socket;
    private List<WorkerConnection> workers;

    public MasterThread(Socket socket, List<WorkerConnection> workers) {
        this.socket = socket;
        this.workers = workers;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            String request;
            while ((request = in.readLine()) != null) {
                if (workers.isEmpty()) {
                    out.println("No workers available to process request: " + request);
                    continue;
                }
                String[] parts = request.split(" ", 2);
                String command = parts[0];
                String data = parts.length > 1 ? parts[1] : "";
                switch (command) {
                    case "ADD_STORE":
                        String storeName = extractStoreName(data);
                        WorkerConnection worker = getWorkerForStore(storeName);
                        String response = worker.sendRequest(request);
                        out.println(response);
                        break;
                    case "SEARCH":
                        List<String> results = new ArrayList<>();
                        for (WorkerConnection w : workers) {
                            String workerResponse = w.sendRequest("FILTER " + data);
                            results.addAll(Arrays.asList(workerResponse.split("\n")));
                        }
                        out.println(String.join("\n", results));
                        break;
                    case "BUY":
                        String[] buyParts = data.split(" ");
                        String buyStoreName = buyParts[0];
                        WorkerConnection buyWorker = getWorkerForStore(buyStoreName);
                        String buyResponse = buyWorker.sendRequest(request);
                        out.println(buyResponse);
                        break;
                    default:
                        out.println("Unknown command: " + command);
                }
            }
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
        }
    }

    private String extractStoreName(String data) {
        // Simplified: assumes data is "storeName" for now; in practice, parse JSON
        return data.split(" ")[0];
    }

    private WorkerConnection getWorkerForStore(String storeName) {
        int hash = storeName.hashCode();
        int workerIndex = Math.abs(hash) % workers.size();
        return workers.get(workerIndex);
    }
}

class WorkerConnection {
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
            out.println(request);
            return in.readLine(); // Adjust for multi-line responses if needed
        } catch (IOException e) {
            // Reconnect on failure
            connect();
            out.println(request);
            return in.readLine();
        }
    }

    public void close() throws IOException {
        if (socket != null) socket.close();
        if (out != null) out.close();
        if (in != null) in.close();
    }
}