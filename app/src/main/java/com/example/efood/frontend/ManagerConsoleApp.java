package com.example.efood.frontend;

import java.io.*;
import java.net.*;
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
                System.out.println("Enter command (ADD_STORE, ADD_PRODUCT, GET_FOOD_STATS, EXIT):");
                String command = scanner.nextLine();
                if (command.equalsIgnoreCase("EXIT")) break;
                String data = "";
                switch (command) {
                    case "ADD_STORE":
                        System.out.println("Enter store name:");
                        data = scanner.nextLine();
                        break;
                    case "ADD_PRODUCT":
                        System.out.println("Enter store name, product name, type, amount, price (space-separated):");
                        data = scanner.nextLine();
                        break;
                    case "GET_FOOD_STATS":
                        System.out.println("Enter food category:");
                        data = scanner.nextLine();
                        break;
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