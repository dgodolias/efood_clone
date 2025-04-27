package com.example.backend;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Master {
    private static final int PORT = 8080;
    private static final int REDUCER_PORT = 8090; 
    private static final String DEFAULT_REDUCER_HOST = "localhost"; 
    private String reducerHost; 
    private static final int REPLICATION_FACTOR = 3;
    private List<WorkerConnection> workers;
    private List<Process> workerProcesses; 
    private Process reducerProcess; 
    private Map<String, List<WorkerConnection>> storeToWorkers;
    private ScheduledExecutorService heartbeatScheduler;
    private PrintWriter out;
    private boolean isLocalMode = true; 
    private InetAddress bindAddress; 

    public Master(int startPort, boolean localMode, String reducerHost, List<String> remoteWorkerAddresses) throws IOException {
        this.isLocalMode = localMode;
        this.reducerHost = (reducerHost != null && !reducerHost.isEmpty()) ? reducerHost : DEFAULT_REDUCER_HOST;
        
        this.bindAddress = isLocalMode ? 
                InetAddress.getByName("localhost") : 
                null; 
        
        workers = new ArrayList<>();
        workerProcesses = new ArrayList<>();
        storeToWorkers = new HashMap<>();

        out = new PrintWriter(System.out, true);

      
        if (isLocalMode) {
            deleteDirectory(new File("data/temp_workers_data"));
        }

        
        int storeCount = countStoresInJsonFile();
        
        List<String> workerAddresses = new ArrayList<>(); 

        if (isLocalMode) {
            
            int workerCount = Math.max(1, (int)Math.sqrt(storeCount));
            
            
            System.out.println("Starting in LOCAL mode with " + workerCount + " workers for " + storeCount + " stores");
            for (int i = 0; i < workerCount; i++) {
                int workerPort = startPort + i;
                spawnWorker(workerPort);
                WorkerConnection wc = new WorkerConnection("localhost", workerPort);
                workers.add(wc);
                workerAddresses.add("localhost:" + workerPort);
                System.out.println("Started and connected to worker at localhost:" + workerPort);
            }
            
            
            spawnReducer(workerAddresses);
        } else {
            
            System.out.println("Starting in DISTRIBUTED mode with " + remoteWorkerAddresses.size() + " remote workers");
            for (String address : remoteWorkerAddresses) {
                String[] parts = address.split(":");
                if (parts.length == 2) {
                    String host = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    try {
                        WorkerConnection wc = new WorkerConnection(host, port);
                        workers.add(wc);
                        workerAddresses.add(address);
                        System.out.println("Connected to remote worker at " + address);
                    } catch (IOException e) {
                        System.err.println("Failed to connect to remote worker at " + address + ": " + e.getMessage());
                    }
                } else {
                    System.err.println("Invalid worker address format: " + address);
                }
            }
            
            
            System.out.println("Using remote Reducer at " + this.reducerHost + ":" + REDUCER_PORT);
        }

        // Verify Reducer connectivity before proceeding
        if (!verifyReducerConnectivity()) {
            throw new IOException("Failed to connect to the Reducer at " + this.reducerHost + ":" + REDUCER_PORT + 
                                 ". Make sure the Reducer is running before starting the Master.");
        }
        
        loadInitialStores();
        startHeartbeat();
    }
    
    /**
     * Verifies that the Master can connect to the Reducer.
     * This makes the Reducer an essential component for the Master to start.
     * 
     * @return true if the connection was successful, false otherwise
     */
    private boolean verifyReducerConnectivity() {
        try (Socket socket = new Socket(reducerHost, REDUCER_PORT)) {
            System.out.println("Successfully connected to Reducer at " + reducerHost + ":" + REDUCER_PORT);
            return true;
        } catch (IOException e) {
            System.err.println("ERROR: Cannot connect to Reducer at " + reducerHost + ":" + REDUCER_PORT);
            System.err.println("The Reducer must be running for the Master to function.");
            return false;
        }
    }

    private int countStoresInJsonFile() {
        try {
            File storesFile = new File("data/stores.json");
            if (!storesFile.exists()) {
                System.out.println("No stores.json file found, using default worker count");
                return 2; 
            }

            String jsonContent = new String(Files.readAllBytes(Paths.get("data/stores.json"))).trim();
            if (!jsonContent.startsWith("[") || !jsonContent.endsWith("]")) {
                System.err.println("Invalid JSON format in stores.json");
                return 2; 
            }

            List<String> storeJsons = parseStoreJsons(jsonContent);
            return storeJsons.size();
        } catch (IOException e) {
            System.err.println("Error reading stores.json: " + e.getMessage());
            return 2;
        }
    }

    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    private void loadInitialStores() throws IOException {
        File storesFile = new File("data/stores.json");
        try (BufferedReader reader = new BufferedReader(new FileReader(storesFile))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) content.append(line).append("\n");

        }

        if (!storesFile.exists()) {
            System.out.println("No initial stores found in data/stores.json");
            return;
        }
        String jsonContent = new String(Files.readAllBytes(Paths.get("data/stores.json"))).trim();
        if (!jsonContent.startsWith("[") || !jsonContent.endsWith("]")) {
            System.err.println("Invalid JSON format in stores.json");
            return;
        }

        // First try to load persistent worker assignments from a file
        boolean loadedFromFile = loadWorkerAssignments();
        if (loadedFromFile) {
            System.out.println("Loaded store-to-worker assignments from persistent storage.");
        }

        List<String> storeJsons = parseStoreJsons(jsonContent);
        for (String storeJson : storeJsons) {
            String storeName = extractField(storeJson, "StoreName");
            if (storeName.isEmpty()) {
                System.err.println("Failed to extract StoreName from: " + storeJson);
                continue;
            }

            List<WorkerConnection> assignedWorkers = getWorkersForStore(storeName);
            for (WorkerConnection worker : assignedWorkers) {
                try {
                    worker.sendRequest("ADD_STORE " + storeJson);
                } catch (IOException e) {
                    System.err.println("Failed to send store to worker: " + e.getMessage());
                }
            }
        }
        
        // Save worker assignments for future restarts
        saveWorkerAssignments();
    }
    
    /**
     * Save the current store-to-worker assignments to a file for persistence across restarts
     */
    private void saveWorkerAssignments() {
        File assignmentsFile = new File("data/worker_assignments.txt");
        try (PrintWriter writer = new PrintWriter(new FileWriter(assignmentsFile))) {
            for (Map.Entry<String, List<WorkerConnection>> entry : storeToWorkers.entrySet()) {
                String storeName = entry.getKey();
                List<Integer> workerPorts = entry.getValue().stream()
                    .map(WorkerConnection::getPort)
                    .collect(Collectors.toList());
                
                writer.println(storeName + ":" + workerPorts.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(",")));
            }
            System.out.println("Saved store-to-worker assignments to " + assignmentsFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save worker assignments: " + e.getMessage());
        }
    }
    
    /**
     * Load store-to-worker assignments from a file
     * @return true if assignments were loaded, false otherwise
     */
    private boolean loadWorkerAssignments() {
        File assignmentsFile = new File("data/worker_assignments.txt");
        if (!assignmentsFile.exists()) {
            return false;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(assignmentsFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts.length != 2) continue;
                
                String storeName = parts[0];
                String[] workerPortStrs = parts[1].split(",");
                List<WorkerConnection> assignedWorkers = new ArrayList<>();
                
                for (String portStr : workerPortStrs) {
                    int port = Integer.parseInt(portStr);
                    // Find the matching worker connection
                    for (WorkerConnection worker : workers) {
                        if (worker.getPort() == port) {
                            assignedWorkers.add(worker);
                            break;
                        }
                    }
                }
                
                if (!assignedWorkers.isEmpty()) {
                    storeToWorkers.put(storeName, assignedWorkers);
                }
            }
            return true;
        } catch (IOException | NumberFormatException e) {
            System.err.println("Failed to load worker assignments: " + e.getMessage());
            return false;
        }
    }

    private List<String> parseStoreJsons(String jsonContent) {
        List<String> stores = new ArrayList<>();
        jsonContent = jsonContent.substring(1, jsonContent.length() - 1).trim();
        int braceCount = 0;
        int start = -1;
        for (int i = 0; i < jsonContent.length(); i++) {
            char c = jsonContent.charAt(i);
            if (c == '{') {
                if (braceCount == 0) start = i;
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0 && start != -1) {
                    String storeJson = jsonContent.substring(start, i + 1).trim();
                    stores.add(storeJson);
                    start = -1;
                }
            }
        }
        return stores;
    }

    private String extractField(String json, String field) {
        String search = "\"" + field + "\":";
        int start = json.indexOf(search);
        if (start == -1) return "";
        start += search.length();
        if (json.charAt(start) == '"') {
            start++;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } else {
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            return json.substring(start, end).trim();
        }
    }

    private void spawnWorker(int port) throws IOException {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        String className = Worker.class.getName();

        ProcessBuilder pb = new ProcessBuilder(
                javaBin, "-cp", classpath, className, String.valueOf(port)
        );
        pb.inheritIO();
        Process process = pb.start();
        workerProcesses.add(process);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void spawnReducer(List<String> workerAddresses) throws IOException {
        if (!isLocalMode) {
            System.out.println("Skipping local Reducer spawn in distributed mode");
            return;
        }
        
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        String className = Reducer.class.getName();

        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-cp");
        command.add(classpath);
        command.add(className);
        command.addAll(workerAddresses); 

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO(); 
        reducerProcess = pb.start();
        System.out.println("Started Reducer process.");

        
        try {
            Thread.sleep(1000); // Give reducer time to start
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void start() {
        try (ServerSocket serverSocket = bindAddress != null ? 
                new ServerSocket(PORT, 50, bindAddress) : // Bind to localhost in local mode
                new ServerSocket(PORT)) { // Bind to all interfaces (0.0.0.0) in distributed mode
            
            String bindInfo = bindAddress != null ? 
                    "localhost:" + PORT : 
                    "0.0.0.0:" + PORT + " (all interfaces)";
            
            System.out.println("Master Server running on " + bindInfo);
            
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected: " + socket.getInetAddress());
                
                new MasterThread(socket, workers, storeToWorkers, REPLICATION_FACTOR, reducerHost, REDUCER_PORT).start();
            }
        } catch (IOException e) {
            System.err.println("Master server failed: " + e.getMessage());
        } finally {
            shutdownWorkers();
            shutdownReducer(); 
        }
    }

    private void shutdownWorkers() {
        for (Process p : workerProcesses) {
            p.destroy();
        }
        for (WorkerConnection wc : workers) {
            try {
                wc.close();
            } catch (IOException e) {
                System.err.println("Error closing worker connection: " + e.getMessage());
            }
        }
        heartbeatScheduler.shutdown();
    }

    private void shutdownReducer() {
        if (reducerProcess != null) {
            reducerProcess.destroy();
            System.out.println("Stopped Reducer process.");
        }
    }

    private List<WorkerConnection> getWorkersForStore(String storeName) {
        // Normalize storeName by removing surrounding quotes
        String normalizedName = normalizeStoreName(storeName);
        
        // Check all normalized keys in storeToWorkers map
        for (String existingStore : new ArrayList<>(storeToWorkers.keySet())) {
            if (normalizeStoreName(existingStore).equals(normalizedName)) {
                List<WorkerConnection> workers = storeToWorkers.get(existingStore);
                System.out.println("WORKER ASSIGNMENT: Using existing assignment for store '" + normalizedName + "' (stored as '" + existingStore + "'): " +
                    workers.stream()
                        .map(w -> "Worker_" + w.getPort())
                        .collect(Collectors.joining(", ")));
                
                // Update the key if necessary to ensure consistency
                if (!existingStore.equals(storeName)) {
                    storeToWorkers.put(normalizedName, workers);
                    storeToWorkers.remove(existingStore);
                    System.out.println("WORKER ASSIGNMENT: Updated store key from '" + existingStore + "' to '" + normalizedName + "'");
                }
                
                return workers;
            }
        }
        
        if (workers.isEmpty()) {
            System.err.println("WORKER ASSIGNMENT: Cannot assign workers for store '" + normalizedName + "': No workers available.");
            return new ArrayList<>(); 
        }
        
        // Use a consistent hashing algorithm to ensure the same store always gets assigned to the same workers
        int hash = getConsistentHashForStore(normalizedName);
        System.out.println("WORKER ASSIGNMENT: Calculated consistent hash " + hash + " for store '" + normalizedName + "'");
        
        int primaryIndex = hash % workers.size();
        List<WorkerConnection> assignedWorkers = new ArrayList<>();
        for (int i = 0; i < REPLICATION_FACTOR && i < workers.size(); i++) {
            int index = (primaryIndex + i) % workers.size();
            assignedWorkers.add(workers.get(index));
        }
        
        storeToWorkers.put(normalizedName, assignedWorkers);
        
        System.out.println("WORKER ASSIGNMENT: Newly assigned store '" + normalizedName + "' to worker(s): " +
            assignedWorkers.stream()
                .map(w -> "Worker_" + w.getPort())
                .collect(Collectors.joining(", ")));
        
        return assignedWorkers;
    }

    /**
     * Normalize a store name by removing any surrounding quotes
     */
    private String normalizeStoreName(String storeName) {
        if (storeName == null) {
            return "";
        }
        
        // Remove surrounding quotes if present
        String normalized = storeName;
        if (normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        
        // Handle double-quote escaping in JSON
        if (normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        
        System.out.println("WORKER ASSIGNMENT: Normalized store name from '" + storeName + "' to '" + normalized + "'");
        return normalized;
    }

    /**
     * Custom hash function for consistent worker assignment across system restarts
     * This ensures the same store always gets assigned to the same workers,
     * even across different JVM instances
     */
    private int getConsistentHashForStore(String storeName) {
        // Simple but consistent hashing algorithm
        int hash = 0;
        for (int i = 0; i < storeName.length(); i++) {
            hash = 31 * hash + storeName.charAt(i);
        }
        return Math.abs(hash);
    }

    private void startHeartbeat() {
        heartbeatScheduler = Executors.newScheduledThreadPool(1);
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            for (WorkerConnection w : workers) {
                try {
                    String response = w.sendRequest("PING"); 
                    System.out.println("Master received from worker " + w.getPort() + ": " + response); 
                } catch (IOException e) {
                    System.err.println("Worker at " + w.getPort() + " is down");
                }
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    public static void main(String[] args) {
        try {
            int startPort = 8081;
            boolean localMode = true; 
            String reducerHost = DEFAULT_REDUCER_HOST;
            List<String> remoteWorkerAddresses = new ArrayList<>();

            
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("--distributed")) {
                    localMode = false;
                    System.out.println("Distributed mode enabled");
                } else if (args[i].equals("--reducer")) {
                    if (i + 1 < args.length) {
                        reducerHost = args[++i];
                        System.out.println("Using reducer host: " + reducerHost);
                    }
                } else if (args[i].equals("--workers")) {
                    if (i + 1 < args.length) {

                        String workersArg = args[++i];
                        String[] workers = workersArg.split(",");
                        for (String worker : workers) {
                            worker = worker.trim();
                            if (!worker.isEmpty()) {
                                remoteWorkerAddresses.add(worker);
                                System.out.println("Added worker: " + worker);
                            }
                        }
                    }
                }
            }

            System.out.println("Configured with " + remoteWorkerAddresses.size() + " worker(s) in " + 
                               (localMode ? "LOCAL" : "DISTRIBUTED") + " mode");

            Master master = new Master(startPort, localMode, reducerHost, remoteWorkerAddresses);
            master.start();
        } catch (IOException e) {
            System.err.println("Failed to initialize Master: " + e.getMessage());
        }
    }
}

class MasterThread extends Thread {
    private Socket socket;
    private List<WorkerConnection> workers;
    private Map<String, List<WorkerConnection>> storeToWorkers;
    private final int replicationFactor;
    private final String reducerHost;
    private final int reducerPort;
    // Add a map to store intermediate results from workers
    private Map<String, List<String>> intermediateResults;

    public MasterThread(Socket socket, List<WorkerConnection> workers, Map<String, List<WorkerConnection>> storeToWorkers, int replicationFactor, String reducerHost, int reducerPort) {
        this.socket = socket;
        this.workers = workers;
        this.storeToWorkers = storeToWorkers;
        this.replicationFactor = replicationFactor;
        this.reducerHost = reducerHost;
        this.reducerPort = reducerPort;
        this.intermediateResults = new HashMap<>();
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            String request;
            while ((request = in.readLine()) != null) {
                System.out.println("Received command: " + request);
                if (workers.isEmpty()) {
                    out.println("No workers available to process request: " + request);
                    out.println("END"); 
                    continue;
                }
                
                // Verify Reducer connection before processing any request
                try {
                    // Quick check to ensure Reducer is still available
                    Socket reducerSocket = new Socket(reducerHost, reducerPort);
                    reducerSocket.close();
                } catch (IOException e) {
                    System.err.println("ERROR: Cannot connect to Reducer at " + reducerHost + ":" + reducerPort);
                    out.println("ERROR: Reducer is unavailable. The system cannot process requests without a functioning Reducer.");
                    out.println("END");
                    continue;
                }
                
                String[] parts = request.split(" ", 2);
                String command = parts[0];
                String data = parts.length > 1 ? parts[1] : "";

                processCommandWithMapReduce(command, data, out);
            }
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
        }
    }

    /**
     * Process a command using the MapReduce pattern
     * 
     * @param command The command to process
     * @param data The data associated with the command
     * @param out The PrintWriter to send the response to
     */
    private void processCommandWithMapReduce(String command, String data, PrintWriter out) {
        // MAP phase: Distribute command to all workers and collect results
        executeMapPhase(command, data);
        
        // REDUCE phase: Send intermediate results to Reducer for processing
        try {
            String reducerResponse = sendIntermediateResultsToReducer(command);
            System.out.println("Master received from Reducer for " + command + ":\n" + reducerResponse);
            out.println(reducerResponse);
        } catch (IOException e) {
            System.err.println("Master failed to communicate with Reducer: " + e.getMessage());
            out.println("Error: Could not process " + command + " due to Reducer communication failure.");
        }
        out.println("END");
    }

    /**
     * Execute the Map phase by distributing the command to the appropriate workers based on the store
     * This implements active replication by targeting only the workers assigned to a specific store
     * @param command The command to execute
     * @param data The data associated with the command
     */
    private void executeMapPhase(String command, String data) {
        List<String> results = new ArrayList<>();
        String fullCommand = command + (data.isEmpty() ? "" : " " + data);
        
        System.out.println("MAP PHASE: Distributing command to workers: " + fullCommand);
        
        // Extract store name from the command data if possible
        String storeName = extractStoreNameFromCommand(command, data);
        
        if (storeName != null && !storeName.isEmpty()) {
            // Get the assigned workers for this store
            List<WorkerConnection> assignedWorkers = storeToWorkers.get(storeName);
            
            if (assignedWorkers != null && !assignedWorkers.isEmpty()) {
                // ADDED DEBUG PRINT - Show which worker is assigned this command
                System.out.println("DEBUG: Command " + command + " for store '" + storeName + "' assigned to worker(s): " +
                    assignedWorkers.stream().map(w -> "Worker_" + w.getPort()).collect(Collectors.joining(", ")));
                
                // Send command to assigned workers according to replication factor
                executeCommandOnWorkers(fullCommand, assignedWorkers, results);
                System.out.println("Command sent to " + assignedWorkers.size() + " assigned workers for store: " + storeName);
                
                // ADDED DEBUG PRINT - Show which workers should be synced
                System.out.println("DEBUG: Workers that should be synced after this operation: " +
                    workers.stream().map(w -> "Worker_" + w.getPort()).collect(Collectors.joining(", ")));
            } else {
                // New store or store not found in mapping, assign workers and send command
                List<WorkerConnection> newAssignedWorkers = getWorkersForStore(storeName);
                
                // ADDED DEBUG PRINT - Show which worker is newly assigned this command
                System.out.println("DEBUG: Command " + command + " for store '" + storeName + "' newly assigned to worker(s): " +
                    newAssignedWorkers.stream().map(w -> "Worker_" + w.getPort()).collect(Collectors.joining(", ")));
                
                executeCommandOnWorkers(fullCommand, newAssignedWorkers, results);
                System.out.println("Command sent to " + newAssignedWorkers.size() + " newly assigned workers for store: " + storeName);
                
                // ADDED DEBUG PRINT - Show which workers should be synced
                System.out.println("DEBUG: Workers that should be synced after this operation: " +
                    workers.stream().map(w -> "Worker_" + w.getPort()).collect(Collectors.joining(", ")));
            }
        } else {
            // For commands that don't target a specific store (like FILTER_STORES), send to all workers
            executeCommandOnWorkers(fullCommand, workers, results);
            System.out.println("Command sent to all " + workers.size() + " workers (no specific store targeted)");
        }
        
        // Store the intermediate results for the reducer phase
        intermediateResults.put(command, results);
    }

    /**
     * Execute a command on the specified list of worker connections
     * @param fullCommand The complete command to execute
     * @param targetWorkers The list of workers to send the command to
     * @param results List to collect the results
     */
    private void executeCommandOnWorkers(String fullCommand, List<WorkerConnection> targetWorkers, List<String> results) {
        for (WorkerConnection worker : targetWorkers) {
            try {
                String response = worker.sendRequest(fullCommand);
                if (response != null && !response.isEmpty()) {
                    results.add(response);
                    System.out.println("Worker " + worker.getPort() + " responded: " + response);
                }
            } catch (IOException e) {
                System.err.println("Error communicating with worker " + worker.getPort() + ": " + e.getMessage());
                // If a worker is down, we should implement failover to other replicas
                // This will be handled in the Reducer when processing the results
            }
        }
    }

    /**
     * Extract the store name from various commands
     * @param command The command type
     * @param data The command data
     * @return The extracted store name, or null if not applicable
     */
    private String extractStoreNameFromCommand(String command, String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        
        String extractedName = null;
        
        switch (command) {
            case "ADD_STORE":
                // Extract from JSON format
                String storeNameField = "\"StoreName\":";
                int start = data.indexOf(storeNameField);
                if (start != -1) {
                    start += storeNameField.length();
                    if (data.charAt(start) == '"') {
                        start++;
                        int end = data.indexOf("\"", start);
                        if (end != -1) {
                            extractedName = data.substring(start, end);
                        }
                    }
                }
                break;
                
            case "ADD_PRODUCT":
            case "REMOVE_PRODUCT":
            case "BUY":
                // These commands have store name as first part before comma
                String[] commaParts = data.split(",", 2);
                if (commaParts.length > 0) {
                    extractedName = commaParts[0].trim();
                }
                break;
                
            case "REVIEW":
            case "GET_STORE_DETAILS":
                // Special handling for multi-word store names
                // First check if the store name is a known store with multiple words
                for (String existingStore : storeToWorkers.keySet()) {
                    String normalizedExisting = normalizeStoreName(existingStore);
                    // If data starts with the existing store name, use that full name
                    if (data.startsWith(normalizedExisting)) {
                        extractedName = normalizedExisting;
                        System.out.println("STORE NAME EXTRACTION: Found full store name match '" + normalizedExisting + "' in command data");
                        break;
                    }
                }
                
                // If no match found in existing stores, try to get first word as a fallback
                if (extractedName == null) {
                    // Check if the command data might contain a store name with multiple words
                    for (String existingStore : storeToWorkers.keySet()) {
                        String normalizedExisting = normalizeStoreName(existingStore);
                        String[] existingWords = normalizedExisting.split("\\s+");
                        if (existingWords.length > 1 && data.startsWith(existingWords[0])) {
                            // If first word of an existing store matches the start of data,
                            // check if data might contain the full store name
                            if (data.length() >= normalizedExisting.length() && 
                                data.substring(0, normalizedExisting.length()).equalsIgnoreCase(normalizedExisting)) {
                                extractedName = normalizedExisting;
                                System.out.println("STORE NAME EXTRACTION: Expanded partial match '" + 
                                                 existingWords[0] + "' to full store name '" + normalizedExisting + "'");
                                break;
                            }
                        }
                    }
                    
                    // If still no match, default to first word
                    if (extractedName == null) {
                        String[] spaceParts = data.split("\\s+", 2);
                        if (spaceParts.length > 0) {
                            extractedName = spaceParts[0].trim();
                            System.out.println("STORE NAME EXTRACTION: Using first word '" + extractedName + 
                                             "' as store name from: '" + data + "'");
                        }
                    }
                }
                break;
                
            case "FILTER_STORES":
            case "FIND_STORES_WITHIN_RANGE":
            case "GET_SALES_BY_STORE_TYPE_CATEGORY":
            case "GET_SALES_BY_PRODUCT_CATEGORY":
            case "GET_SALES_BY_PRODUCT":
                // These commands don't target a specific store, they require querying all workers
                return null;
                
            default:
                return null;
        }
        
        // Normalize the extracted store name before returning it
        if (extractedName != null) {
            String normalizedName = normalizeStoreName(extractedName);
            System.out.println("STORE NAME EXTRACTION: Command '" + command + "' - Final normalized store name: '" + normalizedName + "'");
            return normalizedName;
        }
        
        return null;
    }

    /**
     * Get the list of worker connections assigned to handle a specific store
     * This implements active replication by maintaining multiple copies of each store
     * @param storeName The name of the store
     * @return List of WorkerConnection instances assigned to this store
     */
    private List<WorkerConnection> getWorkersForStore(String storeName) {
        // Normalize storeName by removing surrounding quotes
        String normalizedName = normalizeStoreName(storeName);
        
        // Check all normalized keys in storeToWorkers map
        for (String existingStore : new ArrayList<>(storeToWorkers.keySet())) {
            if (normalizeStoreName(existingStore).equals(normalizedName)) {
                List<WorkerConnection> workers = storeToWorkers.get(existingStore);
                
                // Update the key if necessary to ensure consistency
                if (!existingStore.equals(storeName)) {
                    storeToWorkers.put(normalizedName, workers);
                    storeToWorkers.remove(existingStore);
                }
                
                return workers;
            }
        }
        
        // If workers list is empty, return an empty list
        if (workers.isEmpty()) {
            System.err.println("WORKER ASSIGNMENT (Thread): Cannot assign workers for store '" + normalizedName + "': No workers available.");
            return new ArrayList<>(); 
        }
        
        // Use a consistent hashing algorithm to ensure the same store always gets assigned to the same workers
        int hash = getConsistentHashForStore(normalizedName);
        System.out.println("WORKER ASSIGNMENT (Thread): Calculated consistent hash " + hash + " for store '" + normalizedName + "'");
        
        int primaryIndex = hash % workers.size();
        List<WorkerConnection> assignedWorkers = new ArrayList<>();
        for (int i = 0; i < replicationFactor && i < workers.size(); i++) {
            int index = (primaryIndex + i) % workers.size();
            assignedWorkers.add(workers.get(index));
        }
        
        // Store the assignment for future use
        storeToWorkers.put(normalizedName, assignedWorkers);
        System.out.println("WORKER ASSIGNMENT (Thread): Newly assigned store '" + normalizedName + "' to worker(s): " +
            assignedWorkers.stream()
                .map(w -> "Worker_" + w.getPort())
                .collect(Collectors.joining(", ")));
        
        return assignedWorkers;
    }

    /**
     * Normalize a store name by removing any surrounding quotes
     */
    private String normalizeStoreName(String storeName) {
        if (storeName == null) {
            return "";
        }
        
        // Remove surrounding quotes if present
        String normalized = storeName;
        if (normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        
        // Handle double-quote escaping in JSON
        if (normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        
        return normalized;
    }

    /**
     * Custom hash function for consistent worker assignment across system restarts
     * This ensures the same store always gets assigned to the same workers,
     * even across different JVM instances
     */
    private int getConsistentHashForStore(String storeName) {
        // Simple but consistent hashing algorithm
        int hash = 0;
        for (int i = 0; i < storeName.length(); i++) {
            hash = 31 * hash + storeName.charAt(i);
        }
        return Math.abs(hash);
    }

    /**
     * Send the intermediate results collected during the Map phase to the Reducer
     * @param command The command that was executed in the Map phase
     * @return The final result from the Reducer after processing the intermediate results
     */
    private String sendIntermediateResultsToReducer(String command) throws IOException {
        StringBuilder responseBuilder = new StringBuilder();
        
        try (Socket reducerSocket = new Socket(reducerHost, reducerPort);
             PrintWriter reducerOut = new PrintWriter(reducerSocket.getOutputStream(), true);
             BufferedReader reducerIn = new BufferedReader(new InputStreamReader(reducerSocket.getInputStream()))) {

            // Get the intermediate results for this command
            List<String> mapResults = intermediateResults.getOrDefault(command, new ArrayList<>());
            
            // Send the command and all map results to the reducer
            reducerOut.println("REDUCE " + command); 
            for (String result : mapResults) {
                reducerOut.println("MAP_RESULT " + result);
            }
            reducerOut.println("END_MAP_RESULTS");

            String line;
            while ((line = reducerIn.readLine()) != null && !line.equals("END_REDUCER")) {
                if (responseBuilder.length() > 0) {
                    responseBuilder.append("\n");
                }
                responseBuilder.append(line);
            }
        } 
        return responseBuilder.toString();
    }
    
}