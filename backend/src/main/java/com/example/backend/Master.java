package com.example.backend;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    private Map<WorkerConnection, Boolean> workerHealth;
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
        workerHealth = new ConcurrentHashMap<>();

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
                workerHealth.put(wc, true);
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
                        workerHealth.put(wc, true);
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

        // Try to connect to the Reducer, but continue even if it's not available
        verifyReducerConnectivity();
        
        loadInitialStores();
        startHeartbeat();
    }
    
    private boolean verifyReducerConnectivity() {
        try (Socket socket = new Socket(reducerHost, REDUCER_PORT)) {
            System.out.println("Successfully connected to Reducer at " + reducerHost + ":" + REDUCER_PORT);
            return true;
        } catch (IOException e) {
            System.err.println("WARNING: Cannot connect to Reducer at " + reducerHost + ":" + REDUCER_PORT);
            System.err.println("The Reducer is required for processing requests but the Master will continue running.");
            System.err.println("The Master will periodically check for Reducer availability.");
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
                    workerHealth.put(worker, false);
                }
            }
        }
        
        saveWorkerAssignments();
    }
    
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
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void start() {
        try (ServerSocket serverSocket = bindAddress != null ? 
                new ServerSocket(PORT, 50, bindAddress) : 
                new ServerSocket(PORT)) { 
            
            String bindInfo = bindAddress != null ? 
                    "localhost:" + PORT : 
                    "0.0.0.0:" + PORT + " (all interfaces)";
            
            System.out.println("Master Server initialized on " + bindInfo);
            
            // Print actual IP addresses that can be used to connect
            try {
                System.out.println("Available Master IP addresses for connections:");
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface iface = interfaces.nextElement();
                    // Skip loopback interfaces and disabled interfaces
                    if (iface.isLoopback() || !iface.isUp()) {
                        continue;
                    }

                    Enumeration<InetAddress> addresses = iface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        // Skip IPv6 addresses and loopback addresses
                        if (addr instanceof Inet6Address || addr.isLoopbackAddress()) {
                            continue;
                        }
                        System.out.println(" - " + addr.getHostAddress() + ":" + PORT);
                    }
                }
            } catch (SocketException e) {
                System.out.println("Could not determine IP addresses");
            }
            
            System.out.println("Waiting for Reducer to be available before accepting connections...");
            
            // Wait for the Reducer to be available before accepting connections
            boolean reducerAvailable = false;
            int retryCount = 0;
            while (!reducerAvailable) {
                try {
                    Socket reducerSocket = new Socket(reducerHost, REDUCER_PORT);
                    reducerSocket.close();
                    reducerAvailable = true;
                    System.out.println("Successfully connected to Reducer at " + reducerHost + ":" + REDUCER_PORT);
                } catch (IOException e) {
                    retryCount++;
                    if (retryCount == 1 || retryCount % 10 == 0) { // Show message on first attempt and every 10 attempts
                        System.err.println("WARNING: Cannot connect to Reducer at " + reducerHost + ":" + REDUCER_PORT);
                        System.err.println("The Reducer is required for processing requests. Retrying... (attempt " + retryCount + ")");
                    }
                    
                    // Wait before retrying
                    try {
                        Thread.sleep(2000); // Wait 2 seconds between attempts
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            
            System.out.println("Reducer is available. Master Server is now accepting connections on " + bindInfo);
            
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected: " + socket.getInetAddress());
                
                new MasterThread(socket, workers, storeToWorkers, REPLICATION_FACTOR, reducerHost, REDUCER_PORT, workerHealth).start();
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
        String normalizedName = normalizeStoreName(storeName);
        
        for (String existingStore : new ArrayList<>(storeToWorkers.keySet())) {
            if (normalizeStoreName(existingStore).equals(normalizedName)) {
                List<WorkerConnection> workers = storeToWorkers.get(existingStore);
                // System.out.println("WORKER ASSIGNMENT: Using existing assignment for store '" + normalizedName + "' (stored as '" + existingStore + "'): " +
                //     workers.stream()
                //         .map(w -> "Worker_" + w.getPort())
                //         .collect(Collectors.joining(", ")));
                
                if (!existingStore.equals(storeName)) {
                    storeToWorkers.put(normalizedName, workers);
                    storeToWorkers.remove(existingStore);
                    //System.out.println("WORKER ASSIGNMENT: Updated store key from '" + existingStore + "' to '" + normalizedName + "'");
                }
                
                return workers;
            }
        }
        
        if (workers.isEmpty()) {
            System.err.println("WORKER ASSIGNMENT: Cannot assign workers for store '" + normalizedName + "': No workers available.");
            return new ArrayList<>(); 
        }
        
        int hash = getConsistentHashForStore(normalizedName);
        System.out.println("WORKER ASSIGNMENT: Calculated consistent hash " + hash + " for store '" + normalizedName + "'");
        
        int primaryIndex = hash % workers.size();
        List<WorkerConnection> assignedWorkers = new ArrayList<>();
        
        WorkerConnection primaryWorker = workers.get(primaryIndex);
        assignedWorkers.add(primaryWorker);
        
        for (int i = 1; i < REPLICATION_FACTOR && i < workers.size(); i++) {
            int index = (primaryIndex + i) % workers.size();
            assignedWorkers.add(workers.get(index));
        }
        
        storeToWorkers.put(normalizedName, assignedWorkers);
        
        System.out.println("WORKER ASSIGNMENT: Newly assigned store '" + normalizedName + "' with PRIMARY worker " + 
                          primaryWorker.getPort() + " and backup workers: " +
                          assignedWorkers.stream()
                              .skip(1)
                              .map(w -> "Worker_" + w.getPort())
                              .collect(Collectors.joining(", ")));
        
        return assignedWorkers;
    }

    private String normalizeStoreName(String storeName) {
        if (storeName == null) {
            return "";
        }
        
        String normalized = storeName;
        if (normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        
        if (normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        
        //System.out.println("WORKER ASSIGNMENT: Normalized store name from '" + storeName + "' to '" + normalized + "'");
        return normalized;
    }

    private int getConsistentHashForStore(String storeName) {
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
                    if (workerHealth.getOrDefault(w, true) == false) {
                        System.out.println("WORKER RECOVERY: Worker " + w.getPort() + " is back online");
                    }
                    workerHealth.put(w, true);
                } catch (IOException e) {
                    System.err.println("Worker at " + w.getPort() + " is down: " + e.getMessage());
                    if (workerHealth.getOrDefault(w, true) == true) {
                        workerHealth.put(w, false);
                        reassignStoresFromFailedWorker(w);
                    }
                }
            }
        }, 0, 5, TimeUnit.SECONDS);
    }
    
    private void reassignStoresFromFailedWorker(WorkerConnection failedWorker) {
        List<String> affectedStores = new ArrayList<>();
        
        for (Map.Entry<String, List<WorkerConnection>> entry : storeToWorkers.entrySet()) {
            if (entry.getValue().contains(failedWorker)) {
                affectedStores.add(entry.getKey());
            }
        }
        
        System.out.println("WORKER FAILOVER: Worker " + failedWorker.getPort() + " has failed. " +
                          "Reassigning " + affectedStores.size() + " affected stores.");
        
        for (String storeName : affectedStores) {
            List<WorkerConnection> currentWorkers = storeToWorkers.get(storeName);
            boolean wasPrimary = currentWorkers.indexOf(failedWorker) == 0;
            currentWorkers.remove(failedWorker);
            
            if (currentWorkers.isEmpty()) {
                List<WorkerConnection> newWorkers = getWorkersForStore(storeName);
                System.out.println("WORKER FAILOVER: All workers for store '" + storeName + 
                                  "' have failed. Assigned completely new workers.");
                continue;
            }
            
            if (wasPrimary) {
                System.out.println("WORKER FAILOVER: Primary worker for store '" + storeName + 
                                  "' has failed. Promoting backup worker " + currentWorkers.get(0).getPort() + 
                                  " to primary.");
            }
            
            if (currentWorkers.size() < REPLICATION_FACTOR) {
                for (WorkerConnection w : workers) {
                    if (!currentWorkers.contains(w) && workerHealth.getOrDefault(w, false)) {
                        currentWorkers.add(w);
                        System.out.println("WORKER FAILOVER: Added worker " + w.getPort() + 
                                          " as new backup for store '" + storeName + 
                                          "' to maintain replication factor of " + REPLICATION_FACTOR);
                        
                        try {
                            WorkerConnection healthyWorker = currentWorkers.get(0);
                            String storeData = healthyWorker.sendRequest("GET_STORE_DETAILS " + storeName);
                            
                            if (storeData != null && !storeData.isEmpty() && !storeData.startsWith("ERROR")) {
                                w.sendRequest("ADD_STORE " + storeData);
                                System.out.println("WORKER FAILOVER: Successfully synchronized store '" + 
                                                 storeName + "' to new worker " + w.getPort());
                            } else {
                                System.err.println("WORKER FAILOVER: Failed to get store data for '" + 
                                                 storeName + "' from healthy worker " + healthyWorker.getPort());
                            }
                        } catch (IOException e) {
                            System.err.println("WORKER FAILOVER: Error syncing store '" + storeName + 
                                             "' to new worker " + w.getPort() + ": " + e.getMessage());
                        }
                        break;
                    }
                }
            }
            
            storeToWorkers.put(storeName, currentWorkers);
        }
        
        saveWorkerAssignments();
    }

    public static void main(String[] args) {
        try {
            int startPort = 8081;
            boolean localMode = true; 
            String reducerHost = DEFAULT_REDUCER_HOST;
            List<String> remoteWorkerAddresses = new ArrayList<>();

            // Print usage if requested
            if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
                System.out.println("Usage: java com.example.backend.Master [options]");
                System.out.println("Options:");
                System.out.println("  --distributed             Enable distributed mode (default: local mode)");
                System.out.println("  --reducer <host>          Specify reducer host (default: localhost)");
                System.out.println("  --workers <host:port,...> Comma-separated list of worker addresses");
                System.out.println("Example:");
                System.out.println("  java com.example.backend.Master --distributed --reducer 192.168.1.10 --workers 192.168.1.18:8081,192.168.1.18:8082");
                return;
            }

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
                                // Validate worker address format
                                if (worker.contains(":")) {
                                    remoteWorkerAddresses.add(worker);
                                    System.out.println("Added worker: " + worker);
                                } else {
                                    System.err.println("Invalid worker address format (should be host:port): " + worker);
                                }
                            }
                        }
                    }
                }
            }

            // Always run in distributed mode if worker addresses are provided
            if (!remoteWorkerAddresses.isEmpty()) {
                localMode = false;
                System.out.println("Switching to distributed mode due to provided worker addresses");
            }

            System.out.println("Configured with " + remoteWorkerAddresses.size() + " worker(s) in " + 
                               (localMode ? "LOCAL" : "DISTRIBUTED") + " mode");
            System.out.println("Master will wait for Reducer at " + reducerHost + ":" + REDUCER_PORT + " to become available");

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
    private Map<WorkerConnection, Boolean> workerHealth;
    private Map<String, List<String>> intermediateResults;

    public MasterThread(Socket socket, List<WorkerConnection> workers, 
                      Map<String, List<WorkerConnection>> storeToWorkers, 
                      int replicationFactor, String reducerHost, int reducerPort,
                      Map<WorkerConnection, Boolean> workerHealth) {
        this.socket = socket;
        this.workers = workers;
        this.storeToWorkers = storeToWorkers;
        this.replicationFactor = replicationFactor;
        this.reducerHost = reducerHost;
        this.reducerPort = reducerPort;
        this.workerHealth = workerHealth;
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
                
                // Check if Reducer is available, wait with periodic retries if it's not
                boolean reducerAvailable = false;
                int retryCount = 0;
                while (!reducerAvailable && retryCount < 5) {  // Try up to 5 times
                    try {
                        Socket reducerSocket = new Socket(reducerHost, reducerPort);
                        reducerSocket.close();
                        reducerAvailable = true;
                    } catch (IOException e) {
                        retryCount++;
                        if (retryCount == 1) { // Only print this message on first attempt
                            System.err.println("WARNING: Cannot connect to Reducer at " + reducerHost + ":" + reducerPort);
                            out.println("WARNING: Waiting for Reducer to become available...");
                        }
                        
                        // Exponential backoff for retries
                        try {
                            Thread.sleep(1000 * retryCount);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
                
                if (!reducerAvailable) {
                    System.err.println("ERROR: Cannot connect to Reducer at " + reducerHost + ":" + reducerPort + " after multiple attempts");
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

    private void processCommandWithMapReduce(String command, String data, PrintWriter out) {
        executeMapPhase(command, data);
        
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

    private void executeMapPhase(String command, String data) {
        List<String> results = new ArrayList<>();
        String fullCommand = command + (data.isEmpty() ? "" : " " + data);
        
        System.out.println("MAP PHASE: Distributing command to workers: " + fullCommand);
        
        String storeName = extractStoreNameFromCommand(command, data);
        
        if (storeName != null && !storeName.isEmpty()) {
            List<WorkerConnection> assignedWorkers = null;
            
            for (String existingStore : new ArrayList<>(storeToWorkers.keySet())) {
                String normalizedExisting = normalizeStoreName(existingStore);
                String normalizedRequested = normalizeStoreName(storeName);
                
                if (normalizedExisting.equals(normalizedRequested)) {
                    assignedWorkers = storeToWorkers.get(existingStore);
                    System.out.println("WORKER EXECUTION: Found existing workers for store '" + normalizedRequested + 
                                      "' (stored as '" + existingStore + "')");
                    break;
                }
            }
            
            if (assignedWorkers != null && !assignedWorkers.isEmpty()) {
                System.out.println("WORKER EXECUTION: Command " + command + " for store '" + storeName + "' assigned to worker(s): " +
                    assignedWorkers.stream().map(w -> "Worker_" + w.getPort()).collect(Collectors.joining(", ")));
                
                executeCommandWithFailover(fullCommand, assignedWorkers, results);
            } else {
                List<WorkerConnection> newAssignedWorkers = getWorkersForStore(storeName);
                
                System.out.println("WORKER EXECUTION: Command " + command + " for store '" + storeName + "' newly assigned to worker(s): " +
                    newAssignedWorkers.stream().map(w -> "Worker_" + w.getPort()).collect(Collectors.joining(", ")));
                
                executeCommandWithFailover(fullCommand, newAssignedWorkers, results);
            }
        } else {
            executeCommandOnWorkers(fullCommand, workers, results);
            System.out.println("Command sent to all " + workers.size() + " workers (no specific store targeted)");
        }
        
        intermediateResults.put(command, results);
    }

    private void executeCommandWithFailover(String fullCommand, List<WorkerConnection> targetWorkers, List<String> results) {
        if (targetWorkers.isEmpty()) {
            System.out.println("WORKER EXECUTION: No workers available for command: " + fullCommand);
            results.add("ERROR|No workers available for this command");
            return;
        }
        
        boolean successful = false;
        String primaryResponse = null;
        List<String> errors = new ArrayList<>();
        
        // First try primary worker (first in the list)
        WorkerConnection primaryWorker = targetWorkers.get(0);
        boolean primaryHealthy = workerHealth.getOrDefault(primaryWorker, false);
        
        if (primaryHealthy) {
            try {
                primaryResponse = primaryWorker.sendRequest(fullCommand);
                if (primaryResponse != null && !primaryResponse.isEmpty() && !primaryResponse.startsWith("ERROR")) {
                    results.add(primaryResponse);
                    System.out.println("WORKER EXECUTION: Primary worker " + primaryWorker.getPort() + 
                                     " successfully processed command");
                    successful = true;
                } else {
                    System.out.println("WORKER EXECUTION: Primary worker " + primaryWorker.getPort() + 
                                     " returned error: " + primaryResponse);
                    errors.add("Primary worker " + primaryWorker.getPort() + " error: " + primaryResponse);
                }
            } catch (IOException e) {
                String errorMsg = "WORKER EXECUTION: Primary worker " + primaryWorker.getPort() + 
                                " failed with error: " + e.getMessage();
                System.err.println(errorMsg);
                errors.add(errorMsg);
                
                // Mark the worker as unhealthy
                workerHealth.put(primaryWorker, false);
            }
        } else {
            System.out.println("WORKER EXECUTION: Primary worker " + primaryWorker.getPort() + 
                             " is marked as unhealthy, skipping");
            errors.add("Primary worker " + primaryWorker.getPort() + " is unhealthy");
        }
        
        // If primary worker failed, try backup workers
        if (!successful && targetWorkers.size() > 1) {
            System.out.println("WORKER EXECUTION: Primary worker failed or unhealthy. Trying backup workers...");
            
            for (int i = 1; i < targetWorkers.size(); i++) {
                WorkerConnection backupWorker = targetWorkers.get(i);
                boolean backupHealthy = workerHealth.getOrDefault(backupWorker, false);
                
                if (!backupHealthy) {
                    System.out.println("WORKER EXECUTION: Backup worker " + backupWorker.getPort() + 
                                     " is marked as unhealthy, skipping");
                    continue;
                }
                
                try {
                    String backupResponse = backupWorker.sendRequest(fullCommand);
                    if (backupResponse != null && !backupResponse.isEmpty() && !backupResponse.startsWith("ERROR")) {
                        results.add(backupResponse);
                        System.out.println("WORKER EXECUTION: Backup worker " + backupWorker.getPort() + 
                                         " successfully processed command");
                        successful = true;
                        
                        // If this was a successful backup worker invocation, log it for monitoring
                        System.out.println("WORKER FAILOVER: Successfully executed on backup worker " + 
                                         backupWorker.getPort() + " after primary worker " + 
                                         primaryWorker.getPort() + " failed");
                        break; // Stop after first successful backup
                    } else {
                        System.out.println("WORKER EXECUTION: Backup worker " + backupWorker.getPort() + 
                                         " returned error: " + backupResponse);
                        errors.add("Backup worker " + backupWorker.getPort() + " error: " + backupResponse);
                    }
                } catch (IOException e) {
                    String errorMsg = "WORKER EXECUTION: Backup worker " + backupWorker.getPort() + 
                                    " failed with error: " + e.getMessage();
                    System.err.println(errorMsg);
                    errors.add(errorMsg);
                    
                    // Mark the worker as unhealthy
                    workerHealth.put(backupWorker, false);
                }
            }
        }
        
        // If all workers failed, add errors to results
        if (!successful) {
            System.err.println("WORKER EXECUTION: All workers for this command have failed!");
            results.add("ERROR|All workers failed: " + String.join(", ", errors));
        }
    }

    private void executeCommandOnWorkers(String fullCommand, List<WorkerConnection> targetWorkers, List<String> results) {
        for (WorkerConnection worker : targetWorkers) {
            boolean isHealthy = workerHealth.getOrDefault(worker, false);
            if (!isHealthy) {
                System.out.println("WORKER EXECUTION: Skipping unhealthy worker " + worker.getPort());
                continue;
            }
            
            try {
                String response = worker.sendRequest(fullCommand);
                if (response != null && !response.isEmpty()) {
                    results.add(response);
                    System.out.println("Worker " + worker.getPort() + " responded: " + response);
                }
            } catch (IOException e) {
                System.err.println("Error communicating with worker " + worker.getPort() + ": " + e.getMessage());
                workerHealth.put(worker, false);
            }
        }
    }

    private String extractStoreNameFromCommand(String command, String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        
        String extractedName = null;
        
        switch (command) {
            case "ADD_STORE":
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
                String[] commaParts = data.split(",", 2);
                if (commaParts.length > 0) {
                    extractedName = commaParts[0].trim();
                }
                break;
                
            case "REVIEW":
            case "GET_STORE_DETAILS":
                for (String existingStore : storeToWorkers.keySet()) {
                    String normalizedExisting = normalizeStoreName(existingStore);
                    if (data.startsWith(normalizedExisting)) {
                        extractedName = normalizedExisting;
                        System.out.println("STORE NAME EXTRACTION: Found full store name match '" + normalizedExisting + "' in command data");
                        break;
                    }
                }
                
                if (extractedName == null) {
                    for (String existingStore : storeToWorkers.keySet()) {
                        String normalizedExisting = normalizeStoreName(existingStore);
                        String[] existingWords = normalizedExisting.split("\\s+");
                        if (existingWords.length > 1 && data.startsWith(existingWords[0])) {
                            if (data.length() >= normalizedExisting.length() && 
                                data.substring(0, normalizedExisting.length()).equalsIgnoreCase(normalizedExisting)) {
                                extractedName = normalizedExisting;
                                System.out.println("STORE NAME EXTRACTION: Expanded partial match '" + 
                                                 existingWords[0] + "' to full store name '" + normalizedExisting + "'");
                                break;
                            }
                        }
                    }
                    
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
                return null;
                
            default:
                return null;
        }
        
        if (extractedName != null) {
            extractedName = normalizeStoreName(extractedName);
            //System.out.println("STORE NAME EXTRACTION: Command '" + command + "' - Final normalized store name: '" + extractedName + "'");
        }
        
        return extractedName;
    }

    private List<WorkerConnection> getWorkersForStore(String storeName) {
        String normalizedName = normalizeStoreName(storeName);
        
        for (String existingStore : new ArrayList<>(storeToWorkers.keySet())) {
            if (normalizeStoreName(existingStore).equals(normalizedName)) {
                List<WorkerConnection> workers = storeToWorkers.get(existingStore);
                System.out.println("WORKER ASSIGNMENT (Thread): Using existing assignment for store '" + normalizedName + "' (stored as '" + existingStore + "'): " +
                    workers.stream()
                        .map(w -> "Worker_" + w.getPort())
                        .collect(Collectors.joining(", ")));
                
                if (!existingStore.equals(storeName)) {
                    storeToWorkers.put(normalizedName, workers);
                    storeToWorkers.remove(existingStore);
                    System.out.println("WORKER ASSIGNMENT (Thread): Updated store key from '" + existingStore + "' to '" + normalizedName + "'");
                }
                
                // Filter out unhealthy workers and reorganize if primary is down
                workers = filterAndReorganizeWorkers(workers, normalizedName);
                
                return workers;
            }
        }
        
        if (workers.isEmpty()) {
            System.err.println("WORKER ASSIGNMENT (Thread): Cannot assign workers for store '" + normalizedName + "': No workers available.");
            return new ArrayList<>(); 
        }
        
        // Get only healthy workers
        List<WorkerConnection> healthyWorkers = workers.stream()
            .filter(w -> workerHealth.getOrDefault(w, false))
            .collect(Collectors.toList());
        
        if (healthyWorkers.isEmpty()) {
            System.err.println("WORKER ASSIGNMENT (Thread): No healthy workers available for store '" + normalizedName + "'");
            return new ArrayList<>();
        }
        
        int hash = getConsistentHashForStore(normalizedName);
        System.out.println("WORKER ASSIGNMENT (Thread): Calculated consistent hash " + hash + " for store '" + normalizedName + "'");
        
        int primaryIndex = hash % healthyWorkers.size();
        List<WorkerConnection> assignedWorkers = new ArrayList<>();
        
        WorkerConnection primaryWorker = healthyWorkers.get(primaryIndex);
        assignedWorkers.add(primaryWorker);
        
        for (int i = 1; i < replicationFactor && i < healthyWorkers.size(); i++) {
            int index = (primaryIndex + i) % healthyWorkers.size();
            assignedWorkers.add(healthyWorkers.get(index));
        }
        
        storeToWorkers.put(normalizedName, assignedWorkers);
        
        // System.out.println("WORKER ASSIGNMENT (Thread): Newly assigned store '" + normalizedName + "' with PRIMARY worker " + 
        //                   primaryWorker.getPort() + " and backup workers: " +
        //                   assignedWorkers.stream()
        //                       .skip(1)
        //                       .map(w -> "Worker_" + w.getPort())
        //                       .collect(Collectors.joining(", ")));
        
        return assignedWorkers;
    }
    
    private List<WorkerConnection> filterAndReorganizeWorkers(List<WorkerConnection> workerList, String storeName) {
        List<WorkerConnection> filtered = new ArrayList<>();
        
        // Check if primary is healthy
        WorkerConnection primaryWorker = null;
        if (!workerList.isEmpty()) {
            primaryWorker = workerList.get(0);
        }
        
        boolean primaryHealthy = primaryWorker != null && workerHealth.getOrDefault(primaryWorker, false);
        
        if (primaryHealthy) {
            // Primary is healthy, keep it
            filtered.add(primaryWorker);
            
            // Add healthy backup workers
            for (int i = 1; i < workerList.size(); i++) {
                WorkerConnection backupWorker = workerList.get(i);
                if (workerHealth.getOrDefault(backupWorker, false)) {
                    filtered.add(backupWorker);
                } else {
                    System.out.println("WORKER HEALTH: Removing unhealthy backup worker " + 
                                     backupWorker.getPort() + " from store '" + storeName + "'");
                }
            }
        } else if (primaryWorker != null) {
            // Primary is unhealthy, remove it and promote first healthy backup
            System.out.println("WORKER HEALTH: Primary worker " + primaryWorker.getPort() + 
                             " for store '" + storeName + "' is unhealthy");
            
            boolean foundNewPrimary = false;
            for (int i = 1; i < workerList.size(); i++) {
                WorkerConnection backupWorker = workerList.get(i);
                if (workerHealth.getOrDefault(backupWorker, false)) {
                    // Found a healthy backup worker, promote it to primary
                    filtered.add(backupWorker);
                    foundNewPrimary = true;
                    System.out.println("WORKER FAILOVER: Promoting backup worker " + 
                                     backupWorker.getPort() + " to primary for store '" + 
                                     storeName + "'");
                    
                    // Add remaining healthy backups
                    for (int j = i + 1; j < workerList.size(); j++) {
                        WorkerConnection worker = workerList.get(j);
                        if (workerHealth.getOrDefault(worker, false)) {
                            filtered.add(worker);
                        }
                    }
                    break;
                }
            }
            
            if (!foundNewPrimary) {
                System.out.println("WORKER HEALTH: No healthy backup workers available for store '" + 
                                 storeName + "'. Need to assign new workers.");
                
                // All workers for this store are unhealthy, get new healthy workers
                List<WorkerConnection> healthyWorkers = workers.stream()
                    .filter(w -> workerHealth.getOrDefault(w, false))
                    .filter(w -> !workerList.contains(w))
                    .collect(Collectors.toList());
                
                if (!healthyWorkers.isEmpty()) {
                    // Add a new primary worker
                    filtered.add(healthyWorkers.get(0));
                    System.out.println("WORKER FAILOVER: Assigned new primary worker " + 
                                     healthyWorkers.get(0).getPort() + " for store '" + 
                                     storeName + "'");
                    
                    // Add backup workers up to replication factor
                    for (int i = 1; i < replicationFactor && i < healthyWorkers.size(); i++) {
                        filtered.add(healthyWorkers.get(i));
                        System.out.println("WORKER FAILOVER: Assigned new backup worker " + 
                                         healthyWorkers.get(i).getPort() + " for store '" + 
                                         storeName + "'");
                    }
                }
            }
        }
        
        // If we have healthy workers, update the assignment
        if (!filtered.isEmpty()) {
            storeToWorkers.put(storeName, filtered);
        }
        
        return filtered;
    }

    private String normalizeStoreName(String storeName) {
        if (storeName == null) {
            return "";
        }
        
        String normalized = storeName;
        if (normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        
        if (normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        
        return normalized;
    }

    private int getConsistentHashForStore(String storeName) {
        int hash = 0;
        for (int i = 0; i < storeName.length(); i++) {
            hash = 31 * hash + storeName.charAt(i);
        }
        return Math.abs(hash);
    }

    private String sendIntermediateResultsToReducer(String command) throws IOException {
        StringBuilder responseBuilder = new StringBuilder();
        
        try (Socket reducerSocket = new Socket(reducerHost, reducerPort);
             PrintWriter reducerOut = new PrintWriter(reducerSocket.getOutputStream(), true);
             BufferedReader reducerIn = new BufferedReader(new InputStreamReader(reducerSocket.getInputStream()))) {

            List<String> mapResults = intermediateResults.getOrDefault(command, new ArrayList<>());
            
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