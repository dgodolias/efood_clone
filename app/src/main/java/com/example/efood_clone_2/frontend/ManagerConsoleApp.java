package com.example.efood_clone_2.frontend;

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
                System.out.println("Enter command (ADD_STORE, ADD_PRODUCT, REMOVE_PRODUCT, GET_SALES_BY_STORE_TYPE_CATEGORY, GET_SALES_BY_PRODUCT_CATEGORY, GET_SALES_BY_PRODUCT, EXIT):");
                String command = scanner.nextLine();
                if (command.equalsIgnoreCase("EXIT")) break;
                String data = "";
                switch (command) {
                    case "ADD_STORE":
                        System.out.println("Enter the filename of the store JSON (e.g., store.json):");
                        String filename = scanner.nextLine();
                        try {
                            File currentDir = new File(System.getProperty("user.dir"));
                            File projectRoot = null;

                            while (currentDir != null) {
                                if (new File(currentDir, "data").exists()) {
                                    projectRoot = currentDir;
                                    break;
                                }
                                currentDir = currentDir.getParentFile();
                            }

                            if (projectRoot == null) {
                                throw new IOException("Could not find data directory");
                            }

                            String filePath = projectRoot.getPath() + "/data/to_be_inserted/" + filename;
                            System.out.println("Looking for file at: " + filePath);
                            String fileContent = new String(Files.readAllBytes(Paths.get(filePath)));

                            if (fileContent.trim().startsWith("[")) {
                                int startObject = fileContent.indexOf('{');
                                if (startObject != -1) {
                                    int braceCount = 1;
                                    int endObject = startObject + 1;
                                    while (endObject < fileContent.length() && braceCount > 0) {
                                        char c = fileContent.charAt(endObject);
                                        if (c == '{') braceCount++;
                                        else if (c == '}') braceCount--;
                                        endObject++;
                                    }
                                    data = fileContent.substring(startObject, endObject);
                                } else {
                                    throw new IOException("No valid JSON object found in file");
                                }
                            } else {
                                data = fileContent;
                            }

                            data = data.replaceAll("\\s+", " ").trim();
                            System.out.println("Sending command: ADD_STORE with data: " + data);
                            out.println("ADD_STORE " + data);  // This line was missing
                        } catch (IOException e) {
                            System.err.println("Error reading file: " + e.getMessage());
                            continue;
                        }
                        break;
                    case "ADD_PRODUCT":
                        System.out.println("Enter store name, product name, type, amount, price (comma-separated):");
                        data = scanner.nextLine();
                        out.println("ADD_PRODUCT " + data);
                        break;
                    case "REMOVE_PRODUCT":
                        System.out.println("Enter store name and product name (comma-separated):");
                        data = scanner.nextLine();
                        out.println("REMOVE_PRODUCT " + data);
                        break;
                    case "GET_SALES_BY_STORE_TYPE_CATEGORY":
                        System.out.println("Enter store type category :");
                        data = scanner.nextLine();
                        out.println("GET_SALES_BY_STORE_TYPE_CATEGORY " + data);
                        break;
                    case "GET_SALES_BY_PRODUCT_CATEGORY":
                        System.out.println("Enter product category:");
                        data = scanner.nextLine();
                        out.println("GET_SALES_BY_PRODUCT_CATEGORY " + data);
                        break;
                    case "GET_SALES_BY_PRODUCT":
                        System.out.println("Enter product name:");
                        data = scanner.nextLine();
                        out.println("GET_SALES_BY_PRODUCT " + data);
                        break;
                    default:
                        System.out.println("Unknown command: " + command);
                        continue;
                }

                System.out.println("Response:");
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.equals("END")) break;
                    System.out.println(line);
                }
            }
        } catch (IOException e) {
            System.err.println("Error connecting to Master: " + e.getMessage());
        }
    }
}