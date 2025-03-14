package com.example.backend;

import java.util.Scanner;

public class ManagerConsoleApp {
    private Backend backend;

    public ManagerConsoleApp(Backend backend) {
        this.backend = backend;
    }

    public void start() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("1. Add Store");
            System.out.println("2. Remove Store");
            System.out.println("3. List Stores");
            System.out.println("4. Exit");
            int choice = scanner.nextInt();
            scanner.nextLine(); // Consume newline

            switch (choice) {
                case 1:
                    System.out.println("Enter store name:");
                    String storeName = scanner.nextLine();
                    // Add more input prompts for other store details
                    Store store = new Store(storeName, 0, 0, "", 0, 0, "");
                    backend.addStore(store);
                    break;
                case 2:
                    System.out.println("Enter store name to remove:");
                    String removeStoreName = scanner.nextLine();
                    backend.removeStore(removeStoreName);
                    break;
                case 3:
                    System.out.println("Stores:");
                    for (Store s : backend.getStores()) {
                        System.out.println(s.getStoreName());
                    }
                    break;
                case 4:
                    System.out.println("Exiting...");
                    scanner.close();
                    return;
                default:
                    System.out.println("Invalid choice. Try again.");
            }
        }
    }

    public static void main(String[] args) {
        Backend backend = new Backend();
        ManagerConsoleApp app = new ManagerConsoleApp(backend);
        app.start();
    }
}