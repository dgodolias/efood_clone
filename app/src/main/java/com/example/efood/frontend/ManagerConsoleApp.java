package com.example.efood.frontend;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

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
                System.out.println("Enter command (ADD_STORE, ADD_PRODUCT, REMOVE_PRODUCT, GET_SALES_BY_FOOD_CATEGORY, GET_SALES_BY_PRODUCT_CATEGORY, EXIT):");
                String command = scanner.nextLine();
                if (command.equalsIgnoreCase("EXIT")) break;
                String data = "";
                switch (command) {
                    case "ADD_STORE":
                        System.out.println("Enter the filename of the store JSON (e.g., store.json):");
                        String filename = scanner.nextLine();
                        try {
                            String filePath = "data/to_be_inserted/" + filename;
                            data = new String(Files.readAllBytes(Paths.get(filePath)));
                            data = data.replaceAll("\\s+", " ").trim();
                        } catch (IOException e) {
                            System.err.println("Error reading file: " + e.getMessage());
                            continue;
                        }
                        break;
                    case "ADD_PRODUCT":
                        System.out.println("Enter store name, product name, type, amount, price (comma-separated):");
                        data = scanner.nextLine();
                        break;
                    case "REMOVE_PRODUCT":
                        System.out.println("Enter store name and product name (comma-separated):");
                        data = scanner.nextLine();
                        break;
                    case "GET_SALES_BY_FOOD_CATEGORY":
                        System.out.println("Enter food category:");
                        data = scanner.nextLine();
                        break;
                    case "GET_SALES_BY_PRODUCT_CATEGORY":
                        System.out.println("Enter product category:");
                        data = scanner.nextLine();
                        break;
                    default:
                        System.out.println("Unknown command: " + command);
                        continue;
                }
                out.println(command + " " + data);
                String response = in.readLine();
                System.out.println("Response: " + response);
            }
        } catch (IOException e) {
            System.err.println("Error connecting to Master: " + e.getMessage());
        }
    }
}