package com.example.backend;

import java.io.*;
import java.net.*;
import java.util.*;

public class Master {
    private static final int PORT = 8080;
    private List<WorkerConnection> workers;
    private List<Process> workerProcesses; // To track spawned Worker processes

    public Master(int workerCount, int startPort) throws IOException {
        workers = new ArrayList<>();
        workerProcesses = new ArrayList<>();

        // Dynamically start workers
        for (int i = 0; i < workerCount; i++) {
            int workerPort = startPort + i;
            spawnWorker(workerPort);
            workers.add(new WorkerConnection("localhost", workerPort));
            System.out.println("Started and connected to worker at localhost:" + workerPort);
        }
    }

    private void spawnWorker(int port) throws IOException {
        // Command to launch a new Worker instance
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        String className = Worker.class.getName();

        ProcessBuilder pb = new ProcessBuilder(
                javaBin, "-cp", classpath, className, String.valueOf(port)
        );
        pb.inheritIO(); // Workers share console output for simplicity; adjust as needed
        Process process = pb.start();
        workerProcesses.add(process);

        // Brief delay to ensure worker starts before connection attempt
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
        } finally {
            shutdownWorkers();
        }
    }

    private void shutdownWorkers() {
        for (Process p : workerProcesses) {
            p.destroy();
        }
        for (WorkerConnection wc : workers) {
            try {
                wc.close();
            } catch (IOException e) {
                System.err.println("Error closing worker connection: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        try {
            int workerCount = args.length > 0 ? Integer.parseInt(args[0]) : 2; // Default to 2 workers
            int startPort = 8081; // Starting port for workers
            Master master = new Master(workerCount, startPort);
            master.start();
        } catch (IOException e) {
            System.err.println("Failed to initialize Master: " + e.getMessage());
        }
    }
}

// MasterThread and WorkerConnection classes remain unchanged
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
            return in.readLine();
        } catch (IOException e) {
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