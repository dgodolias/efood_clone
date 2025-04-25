package com.example.backend;

import java.io.*;
import java.net.*;
import java.util.*;

public class Reducer {
    private static final int REDUCER_PORT = 8090; // Port for Reducer
    private List<WorkerConnection> workers;

    public Reducer(List<WorkerConnection> workers) {
        this.workers = workers;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(REDUCER_PORT)) {
            System.out.println("Reducer Server running on port " + REDUCER_PORT);
            while (true) {
                Socket masterSocket = serverSocket.accept();
                System.out.println("Master connected to Reducer: " + masterSocket.getInetAddress());
                new ReducerThread(masterSocket, workers).start();
            }
        } catch (IOException e) {
            System.err.println("Reducer server failed: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: Reducer <workerHost1:workerPort1> <workerHost2:workerPort2> ...");
            System.exit(1);
        }

        List<WorkerConnection> workerConnections = new ArrayList<>();
        for (String workerAddr : args) {
            try {
                String[] parts = workerAddr.split(":");
                if (parts.length != 2) {
                    System.err.println("Invalid worker address format: " + workerAddr);
                    continue;
                }
                String host = parts[0];
                int port = Integer.parseInt(parts[1]);
                workerConnections.add(new WorkerConnection(host, port));
                System.out.println("Reducer configured for worker at " + host + ":" + port);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number in worker address: " + workerAddr);
            } catch (IOException e) {
                System.err.println("Error connecting to worker at " + workerAddr + ": " + e.getMessage());
            }
        }

        if (workerConnections.isEmpty()) {
            System.err.println("No valid worker addresses provided. Reducer cannot start.");
            System.exit(1);
        }

        Reducer reducer = new Reducer(workerConnections);
        reducer.start();
    }
}

class ReducerThread extends Thread {
    private Socket masterSocket;
    private List<WorkerConnection> workers;

    public ReducerThread(Socket masterSocket, List<WorkerConnection> workers) {
        this.masterSocket = masterSocket;
        this.workers = workers;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(masterSocket.getInputStream()));
             PrintWriter out = new PrintWriter(masterSocket.getOutputStream(), true)) {

            String request;
            while ((request = in.readLine()) != null) {
                System.out.println("Reducer received command: " + request);
                if (workers.isEmpty()) {
                    out.println("Reducer Error: No workers configured.");
                    out.println("END_REDUCER"); // Use a distinct end marker
                    continue;
                }

                String[] parts = request.split(" ", 2);
                String command = parts[0];
                String data = parts.length > 1 ? parts[1] : "";

                String result;
                switch (command) {
                    case "GET_SALES_BY_STORE_TYPE_CATEGORY":
                    case "GET_SALES_BY_PRODUCT_CATEGORY":
                    case "GET_SALES_BY_PRODUCT":
                        result = processMapReduce(command, data);
                        break;
                    default:
                        result = "Reducer Error: Unknown command " + command;
                        break;
                }

                // Send multi-line result back to Master
                if (result != null && !result.isEmpty()) {
                    out.println(result);
                }
                out.println("END_REDUCER"); // Signal end of response
            }
        } catch (IOException e) {
            System.err.println("Error handling Master connection in Reducer: " + e.getMessage());
        } finally {
            try {
                masterSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing Master socket in Reducer: " + e.getMessage());
            }
        }
    }

    // This method encapsulates the MapReduce logic for all relevant commands
    private String processMapReduce(String command, String data) {
        Map<String, Integer> salesByStore = new HashMap<>();
        Set<String> processedStores = new HashSet<>(); // Use Set to avoid duplicates from replicas
        int total = 0;

        String workerCommand = command + (data.isEmpty() ? "" : " " + data);
        System.out.println("Reducer querying workers with command: " + workerCommand);

        for (WorkerConnection worker : workers) {
            try {
                String response = worker.sendRequest(workerCommand);
                System.out.println("Reducer received from worker " + worker.getPort() + ": " + response);

                if (response != null && !response.isEmpty()) {
                    String[] storesSales = response.split("\\|"); // Worker returns StoreName:Amount pairs separated by |
                    for (String storeSale : storesSales) {
                        if (!storeSale.isEmpty()) {
                            String[] storeParts = storeSale.split(":");
                            if (storeParts.length == 2) {
                                String storeName = storeParts[0].trim();
                                try {
                                    int amount = Integer.parseInt(storeParts[1].trim());

                                    // Only add/sum if this store hasn't been processed yet from another worker (replica)
                                    if (!processedStores.contains(storeName)) {
                                        salesByStore.put(storeName, amount);
                                        total += amount;
                                        processedStores.add(storeName); // Mark store as processed
                                    }
                                } catch (NumberFormatException nfe) {
                                    System.err.println("Reducer: Invalid amount format from worker " + worker.getPort() + " for store '" + storeName + "': " + storeParts[1]);
                                }
                            } else {
                                System.err.println("Reducer: Invalid store:amount format from worker " + worker.getPort() + ": " + storeSale);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Reducer: Error communicating with worker " + worker.getPort() + " for command '" + command + "': " + e.getMessage());
                // Continue to next worker if one fails
            }
        }

        // Format the result string as expected by the client
        StringBuilder resultBuilder = new StringBuilder();
        for (Map.Entry<String, Integer> entry : salesByStore.entrySet()) {
            if (resultBuilder.length() > 0) {
                resultBuilder.append("\n"); // Newline between store entries
            }
            resultBuilder.append("\"").append(entry.getKey()).append("\": ").append(entry.getValue());
        }
        if (!salesByStore.isEmpty()) {
            resultBuilder.append("\n"); // Newline before total
        }
        resultBuilder.append("\"total\": ").append(total);

        return resultBuilder.toString();
    }
}