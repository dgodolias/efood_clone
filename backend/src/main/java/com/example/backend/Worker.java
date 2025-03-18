package com.example.backend;

import java.io.*;
import java.net.*;
import java.util.*;

public class Worker {
    private static final int PORT = 8081; // Unique port per worker instance
    private Map<String, Store> stores;

    public Worker() {
        this.stores = new HashMap<>();
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Worker running on port " + PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Master connected: " + socket.getInetAddress());
                new WorkerThread(socket, stores).start();
            }
        } catch (IOException e) {
            System.err.println("Worker server failed: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            int port = Integer.parseInt(args[0]);
            Worker worker = new Worker();
            worker.start(port); // Allow custom port via args
        } else {
            new Worker().start();
        }
    }

    public void start(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Worker running on port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                new WorkerThread(socket, stores).start();
            }
        } catch (IOException e) {
            System.err.println("Worker server failed on port " + port + ": " + e.getMessage());
        }
    }
}

class WorkerThread extends Thread {
    private Socket socket;
    private Map<String, Store> stores;

    public WorkerThread(Socket socket, Map<String, Store> stores) {
        this.socket = socket;
        this.stores = stores;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            String request;
            while ((request = in.readLine()) != null) {
                String[] parts = request.split(" ", 2);
                String command = parts[0];
                String data = parts.length > 1 ? parts[1] : "";
                synchronized (stores) { // Ensure thread safety
                    switch (command) {
                        case "ADD_STORE":
                            String storeName = data.split(" ")[0]; // Simplified parsing
                            stores.put(storeName, new Store(storeName, 0, 0, "", 0, 0, ""));
                            out.println("Store added: " + storeName);
                            break;
                        case "FILTER":
                            String foodCategory = data;
                            List<String> results = new ArrayList<>();
                            for (Store store : stores.values()) {
                                if (store.getFoodCategory().equals(foodCategory)) {
                                    results.add(store.getStoreName());
                                }
                            }
                            out.println(String.join("\n", results));
                            break;
                        case "BUY":
                            String[] buyParts = data.split(" ");
                            String buyStoreName = buyParts[0];
                            String productName = buyParts[1];
                            int quantity = Integer.parseInt(buyParts[2]);
                            Store store = stores.get(buyStoreName);
                            if (store != null) {
                                for (Product product : store.getProducts()) {
                                    if (product.getProductName().equals(productName) && product.getAvailableAmount() >= quantity) {
                                        product.setAvailableAmount(product.getAvailableAmount() - quantity);
                                        out.println("Purchase successful");
                                        break;
                                    }
                                }
                            } else {
                                out.println("Store not found");
                            }
                            break;
                        case "GET_FOOD_STATS":
                            String category = data;
                            Map<String, Integer> stats = new HashMap<>();
                            int total = 0;
                            for (Store s : stores.values()) {
                                if (s.getFoodCategory().equals(category)) {
                                    int productCount = s.getProducts().size();
                                    stats.put(s.getStoreName(), productCount);
                                    total += productCount;
                                }
                            }
                            stats.put("total", total);
                            out.println(stats.toString());
                            break;
                        default:
                            out.println("Unknown command: " + command);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error handling request: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
        }
    }
}