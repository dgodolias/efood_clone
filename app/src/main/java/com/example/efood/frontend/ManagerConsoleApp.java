package com.example.efood.frontend;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ManagerConsoleApp {
    private static final String MASTER_HOST = "localhost";
    private static final int MASTER_PORT = 8080;

    public static void main(String[] args) {
        try (Socket socket = new Socket(MASTER_HOST, MASTER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             Scanner scanner = new Scanner(System.in)) {
            System.out.println("Connected to Master at " + MASTER_HOST + ":" + MASTER_PORT);
            while (true) {
                System.out.println("Enter command (ADD_STORE, ADD_PRODUCT, GET_FOOD_STATS, EXIT):");
                String command = scanner.nextLine();
                if (command.equalsIgnoreCase("EXIT")) break;
                switch (command) {
                    case "ADD_STORE":
                        System.out.println("Enter path to store JSON file (e.g., data/to_be_inserted/store.json):");
                        String filePath = scanner.nextLine();
                        try {
                            String jsonData = new String(Files.readAllBytes(Paths.get(filePath)));
                            out.println("ADD_STORE " + jsonData);
                        } catch (IOException e) {
                            System.err.println("Error reading file: " + e.getMessage());
                        }
                        break;
                    case "ADD_PRODUCT":
                        System.out.println("Enter store name, product name, type, amount, price (space-separated):");
                        String productData = scanner.nextLine();
                        out.println("ADD_PRODUCT " + productData);
                        break;
                    case "GET_FOOD_STATS":
                        System.out.println("Enter food category:");
                        String category = scanner.nextLine();
                        out.println("GET_FOOD_STATS " + category);
                        break;
                    default:
                        System.out.println("Unknown command");
                        continue;
                }
                String response = in.readLine();
                System.out.println("Response: " + response);
            }
        } catch (IOException e) {
            System.err.println("Error connecting to Master: " + e.getMessage());
        }
    }
}