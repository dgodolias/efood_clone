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
                // Παράδειγμα επεξεργασίας αιτήματος
                if (request.startsWith("GET_FOOD_STATS")) {
                    String foodCategory = request.split(" ")[1];
                    Map<String, Integer> stats = backend.getFoodCategoryStats(foodCategory);
                    out.println(stats.toString());
                } else if (request.startsWith("GET_PRODUCT_STATS")) {
                    String productType = request.split(" ")[1];
                    Map<String, Integer> stats = backend.getProductCategoryStats(productType);
                    out.println(stats.toString());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
