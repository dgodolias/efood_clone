package com.example.backend;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class BackendServer {
    private static final int PORT = 8080;
    private Backend backend;

    public BackendServer(Backend backend) {
        this.backend = backend;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Backend Server running on port " + PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New worker connected");
                Worker worker = new Worker(socket, backend);
                worker.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Backend backend = new Backend();

        // Προσθήκη δοκιμαστικών δεδομένων
        Store store1 = new Store("Pizza Fun", 37.9932963, 23.733413, "pizzeria", 3, 15, "/usr/bin/images/pizzafun.png");
        store1.addProduct(new Product("margarita", "pizza", 5000, 9.2));
        store1.addProduct(new Product("special", "pizza", 1000, 12));
        store1.addProduct(new Product("chef's Salad", "salad", 100, 5));

        Store store2 = new Store("Pizza Hat", 37.99, 23.74, "pizzeria", 4, 20, "/usr/bin/images/pizzahat.png");
        store2.addProduct(new Product("pepperoni", "pizza", 2000, 10));
        store2.addProduct(new Product("greek Salad", "salad", 50, 6));

        backend.addStore(store1);
        backend.addStore(store2);

        // Ξεκινήστε τον server
        BackendServer server = new BackendServer(backend);
        server.start();
    }
}
