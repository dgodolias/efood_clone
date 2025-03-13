package com.example.backend;

import java.io.*;
import java.net.*;


public class TestClient {
    public static void main(String[] args) {
        try {
            Socket socket = new Socket("localhost", 8080);
            System.out.println("Connected to server"); // Confirm connection
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send a request
            out.println("GET_FOOD_STATS pizzeria");
            String response = in.readLine();
            System.out.println("Response: " + response);

            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}