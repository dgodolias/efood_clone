package com.example.backend;

import java.io.*;
import java.net.Socket;
import java.util.Map;

public class Worker extends Thread {
    private Socket socket;
    private Backend backend;

    public Worker(Socket socket, Backend backend) {
        this.socket = socket;
        this.backend = backend;
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String request;
            while ((request = in.readLine()) != null) {
                if (request.startsWith("GET_FOOD_STATS")) {
                    String foodCategory = request.split(" ")[1];
                    Map<String, Integer> stats = backend.getFoodCategoryStats(foodCategory);
                    out.println(stats.toString()); // Send response back to client
                } else if (request.startsWith("BUY")) {
                    String[] parts = request.split(" ");
                    String storeName = parts[1];
                    String productName = parts[2];
                    int quantity = Integer.parseInt(parts[3]);
                    for (Store store : backend.getStores()) {
                        if (store.getStoreName().equals(storeName)) {
                            for (Product product : store.getProducts()) {
                                if (product.getProductName().equals(productName)) {
                                    product.setAvailableAmount(product.getAvailableAmount() - quantity);
                                    out.println("Purchase successful");
                                    break;
                                }
                            }
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}