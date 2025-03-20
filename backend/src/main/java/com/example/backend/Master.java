package com.example.backend;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Master {
    private static final int PORT = 8080;
    private List<WorkerConnection> workers;
    private List<Process> workerProcesses;

    public Master(int workerCount, int startPort) throws IOException {
        workers = new ArrayList<>();
        workerProcesses = new ArrayList<>();

        // Διαγραφή του φακέλου temp_workers_data κατά την εκκίνηση
        deleteDirectory(new File("data/temp_workers_data"));

        // Εκκίνηση workers
        for (int i = 0; i < workerCount; i++) {
            int workerPort = startPort + i;
            spawnWorker(workerPort);
            workers.add(new WorkerConnection("localhost", workerPort));
            System.out.println("Started and connected to worker at localhost:" + workerPort);
        }

        // Φόρτωση αρχικών καταστημάτων από stores.json
        loadInitialStores();
    }

    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    private void loadInitialStores() throws IOException {
        File storesFile = new File("data/stores.json");
        if (!storesFile.exists()) {
            System.out.println("No initial stores found in data/stores.json");
            return;
        }
        String jsonContent = new String(Files.readAllBytes(Paths.get("data/stores.json"))).trim();
        if (!jsonContent.startsWith("[") || !jsonContent.endsWith("]")) {
            System.err.println("Invalid JSON format in stores.json");
            return;
        }

        List<String> storeJsons = parseStoreJsons(jsonContent);
        for (String storeJson : storeJsons) {
            System.out.println("Parsed store JSON: " + storeJson); // Debugging line
            String storeName = extractField(storeJson, "StoreName");
            if (storeName.isEmpty()) {
                System.err.println("Failed to extract StoreName from: " + storeJson);
                continue;
            }
            WorkerConnection worker = getWorkerForStore(storeName);
            worker.sendRequest("ADD_STORE " + storeJson);
        }
    }

    private List<String> parseStoreJsons(String jsonContent) {
        List<String> stores = new ArrayList<>();
        // Remove outer square brackets
        jsonContent = jsonContent.substring(1, jsonContent.length() - 1).trim();
        if (jsonContent.isEmpty()) return stores;

        int braceCount = 0;
        int start = -1;

        for (int i = 0; i < jsonContent.length(); i++) {
            char c = jsonContent.charAt(i);

            if (c == '{') {
                if (braceCount == 0) {
                    start = i; // Mark the start of a JSON object
                }
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0 && start != -1) {
                    // We've found a complete JSON object
                    String storeJson = jsonContent.substring(start, i + 1).trim();
                    stores.add(storeJson);
                    start = -1; // Reset start for the next object
                }
            }
        }

        return stores;
    }

    private String extractField(String json, String field) {
        String search = "\"" + field + "\":";
        int start = json.indexOf(search);
        if (start == -1) return "";
        start += search.length();
        if (start >= json.length()) return "";

        char firstChar = json.charAt(start);
        if (firstChar == '"') {
            start++;
            int end = json.indexOf("\"", start);
            if (end == -1) return "";
            return json.substring(start, end);
        } else {
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            if (end == -1 || end > json.length()) return "";
            return json.substring(start, end).trim();
        }
    }

    private void spawnWorker(int port) throws IOException {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        String className = Worker.class.getName();

        ProcessBuilder pb = new ProcessBuilder(
                javaBin, "-cp", classpath, className, String.valueOf(port)
        );
        pb.inheritIO();
        Process process = pb.start();
        workerProcesses.add(process);

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
            int workerCount = args.length > 0 ? Integer.parseInt(args[0]) : 2;
            int startPort = 8081;
            Master master = new Master(workerCount, startPort);
            master.start();
        } catch (IOException e) {
            System.err.println("Failed to initialize Master: " + e.getMessage());
        }
    }

    private WorkerConnection getWorkerForStore(String storeName) {
        int hash = storeName.hashCode();
        int workerIndex = Math.abs(hash) % workers.size();
        return workers.get(workerIndex);
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
                        String storeName = extractField(data, "StoreName");
                        if (storeName.isEmpty()) {
                            out.println("Error: Invalid store JSON");
                            continue;
                        }
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
                    case "ADD_PRODUCT":
                    case "GET_FOOD_STATS":
                        String targetName = data.split(" ")[0];
                        WorkerConnection targetWorker = command.equals("GET_FOOD_STATS")
                                ? workers.get(0)
                                : getWorkerForStore(targetName);
                        String workerResponse = targetWorker.sendRequest(request);
                        out.println(workerResponse);
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

    private String extractField(String json, String field) {
        String search = "\"" + field + "\":";
        int start = json.indexOf(search);
        if (start == -1) return "";
        start += search.length();
        if (start >= json.length()) return "";

        char firstChar = json.charAt(start);
        if (firstChar == '"') {
            start++;
            int end = json.indexOf("\"", start);
            if (end == -1) return "";
            return json.substring(start, end);
        } else {
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            if (end == -1 || end > json.length()) return "";
            return json.substring(start, end).trim();
        }
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