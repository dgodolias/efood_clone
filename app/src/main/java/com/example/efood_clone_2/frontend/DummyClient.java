package com.example.efood.frontend;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class DummyClient {
    private static final String MASTER_HOST = "localhost";
    private static final int MASTER_PORT = 8080;

    public static void main(String[] args) {
        try (Socket socket = new Socket(MASTER_HOST, MASTER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             Scanner scanner = new Scanner(System.in)) {
            System.out.println("Connected to Master at " + MASTER_HOST + ":" + MASTER_PORT);
            while (true) {
                System.out.println("Enter request (SEARCH, BUY, EXIT):");
                String request = scanner.nextLine();
                if (request.equalsIgnoreCase("EXIT")) break;
                String data = "";
                if (request.equals("SEARCH")) {
                    System.out.println("Enter food category:");
                    data = scanner.nextLine();
                } else if (request.equals("BUY")) {
                    System.out.println("Enter store name, product name, quantity (space-separated):");
                    data = scanner.nextLine();
                }
                out.println(request + " " + data);
                String response = in.readLine();
                System.out.println("Response from server: " + response);
            }
        } catch (IOException e) {
            System.err.println("Error connecting to Master: " + e.getMessage());
        }
    }
}