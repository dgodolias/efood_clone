package com.example.backend;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Master {
    private static final int PORT = 8080;
    private Backend backend;

    public Master() {
        this.backend = new Backend();
        // Add test data to the server's Backend
        Store store = new Store("PizzaFun", 0, 0, "pizzeria", 3, 10, "");
        backend.addStore(store);
        backend.addProductToStore("PizzaFun", new Product("margarita", "pizza", 5000, 9.2));
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Master Server running on port " + PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected");
                Worker worker = new Worker(socket, backend); // Pass the server's Backend
                worker.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Master master = new Master();
        master.start();
    }
}