package com.example.efood_clone_2.frontend;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ClientConsoleApp {
    private static final String MASTER_HOST = "localhost";
    private static final int MASTER_PORT = 8080;

    public static void main(String[] args) {
        try (Socket socket = new Socket(MASTER_HOST, MASTER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Connected to Master at " + MASTER_HOST + ":" + MASTER_PORT);

            while (true) {
                System.out.println("\nEnter coordinates (latitude,longitude) or 'EXIT' to quit:");
                String input = scanner.nextLine();

                if (input.equalsIgnoreCase("EXIT")) {
                    break;
                }

                // Validate input format
                if (!input.matches("^-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?$")) {
                    System.out.println("Invalid format. Please use: latitude,longitude (e.g., 37.9838,23.7275)");
                    continue;
                }

                // Send the command to find stores within 5km of coordinates
                out.println("FIND_STORES_WITHIN_RANGE " + input);

                // Read and print response
                System.out.println("\nStores within 5km of your location:");
                System.out.println("---------------------------------");

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.equals("END")) break;
                    System.out.println(line);
                }
            }

            System.out.println("Disconnected from server.");

        } catch (IOException e) {
            System.err.println("Error connecting to Master: " + e.getMessage());
        }
    }
}