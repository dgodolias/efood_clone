package com.example.efood_clone_2.frontend;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class ManagerConsoleApp {
    private static final String DEFAULT_MASTER_HOST = "localhost";
    private static final int MASTER_PORT = 8080;

    public static void main(String[] args) {
        String masterHost = DEFAULT_MASTER_HOST;
        int masterPort = MASTER_PORT;
        
        if (args.length > 0) {
            // Check if input contains IP:PORT format
            if (args[0].contains(":")) {
                String[] parts = args[0].split(":");
                if (parts.length == 2) {
                    masterHost = parts[0];
                    try {
                        masterPort = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid port number: " + parts[1] + ". Using default port: " + MASTER_PORT);
                        masterPort = MASTER_PORT;
                    }
                }
            } else {
                masterHost = args[0];
            }
            System.out.println("Using master host: " + masterHost + " and port: " + masterPort);
        }

        String envMasterHost = System.getenv("MASTER_HOST");
        if (envMasterHost != null && !envMasterHost.isEmpty()) {
            // Also handle IP:PORT format from environment variable
            if (envMasterHost.contains(":")) {
                String[] parts = envMasterHost.split(":");
                if (parts.length == 2) {
                    masterHost = parts[0];
                    try {
                        masterPort = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid port number in environment variable: " + parts[1] + ". Using default port: " + masterPort);
                    }
                } else {
                    masterHost = envMasterHost;
                }
            } else {
                masterHost = envMasterHost;
            }
            System.out.println("Using master host from environment: " + masterHost + " and port: " + masterPort);
        }

        // Test if the master is reachable
        System.out.println("Testing connection to Master at " + masterHost + ":" + masterPort + "...");
        try {
            InetAddress inetAddress = InetAddress.getByName(masterHost);
            System.out.println("Resolved " + masterHost + " to " + inetAddress.getHostAddress());
            
            // Try a simple ping to check network connectivity
            boolean reachable = inetAddress.isReachable(5000); // 5 second timeout
            System.out.println("Host is reachable via ICMP ping: " + reachable);
            
            // Try opening a socket with a short timeout
            try (Socket testSocket = new Socket()) {
                testSocket.connect(new InetSocketAddress(masterHost, masterPort), 5000); // 5 second timeout
                System.out.println("Successfully connected to Master socket at " + masterHost + ":" + masterPort);
                testSocket.close();
            } catch (IOException e) {
                System.err.println("Socket connection test failed: " + e.getMessage());
                System.err.println("Please verify the Master is running at the specified address and port.");
                System.err.println("Also check for firewalls that might be blocking the connection.");
                
                // Suggest trying localhost if using a remote address
                if (!masterHost.equals("localhost") && !masterHost.equals("127.0.0.1")) {
                    System.out.println("\nTrying fallback to localhost...");
                    try (Socket localSocket = new Socket()) {
                        localSocket.connect(new InetSocketAddress("localhost", masterPort), 3000);
                        System.out.println("Successfully connected to Master on localhost:" + masterPort);
                        System.out.println("Please use 'localhost' or '127.0.0.1' instead of " + masterHost);
                        // Switch to localhost since it's working
                        masterHost = "localhost";
                    } catch (IOException ex) {
                        System.err.println("Localhost connection also failed: " + ex.getMessage());
                    }
                }
                
                // Let user choose whether to continue despite connection test failure
                System.out.println("\nDo you want to continue anyway? (y/n)");
                Scanner scanner = new Scanner(System.in);
                String response = scanner.nextLine().trim().toLowerCase();
                if (!response.equals("y") && !response.equals("yes")) {
                    System.out.println("Exiting application.");
                    return;
                }
                System.out.println("Continuing despite connection test failure...");
            }
        } catch (IOException e) {
            System.err.println("Unable to resolve host " + masterHost + ": " + e.getMessage());
        }

        try (Socket socket = new Socket(masterHost, masterPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             Scanner scanner = new Scanner(System.in)) {
            System.out.println("Connected to Master at " + masterHost + ":" + masterPort);
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