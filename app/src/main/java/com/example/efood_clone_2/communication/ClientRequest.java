package com.example.efood_clone_2.communication;

import java.io.Serializable;

/**
 * Request object sent from client to server.
 * This class must match the server-side implementation.
 */
public class ClientRequest implements Serializable {
    // Use a specific serialVersionUID to ensure compatibility with server
    private static final long serialVersionUID = 1234567890123456789L;
    
    private String command;
    private String data;

    /**
     * Default constructor required for serialization
     */
    public ClientRequest() {
        this.command = "";
        this.data = "";
    }

    public ClientRequest(String command, String data) {
        this.command = command;
        this.data = data;
    }

    public String getCommand() {
        return command;
    }

    public String getData() {
        return data;
    }
    
    public void setCommand(String command) {
        this.command = command;
    }
    
    public void setData(String data) {
        this.data = data;
    }
    
    @Override
    public String toString() {
        return "ClientRequest{" +
                "command='" + command + '\'' +
                ", data='" + data + '\'' +
                '}';
    }
}