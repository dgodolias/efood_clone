package com.example.frontend;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class DummyClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 8080;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Connected to the server");

            while (true) {
                System.out.println("Enter request (SEARCH or BUY) or 'exit' to quit:");
                String request = scanner.nextLine();
                if (request.equalsIgnoreCase("exit")) {
                    break;
                }

                out.println(request);
                String response = in.readLine();
                System.out.println("Response from server: " + response);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}