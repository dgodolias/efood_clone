package com.example.efood_clone_2.communication;

import java.io.Serializable;

/**
 * Response object sent from server to client. 
 * This class must match the server-side implementation.
 */
public class ClientResponse implements Serializable {
    // Use a specific serialVersionUID to ensure compatibility with server
    private static final long serialVersionUID = 1234567890123456789L;
    
    private String message;
    private boolean success;
    private String errorDetails;

    /**
     * Default constructor required for serialization
     */
    public ClientResponse() {
        this.message = "";
        this.success = false;
        this.errorDetails = null;
    }

    public ClientResponse(String message) {
        this.message = message;
        this.success = true;
        this.errorDetails = null;
    }
    
    public ClientResponse(String message, boolean success) {
        this.message = message;
        this.success = success;
        this.errorDetails = null;
    }
    
    public ClientResponse(String message, boolean success, String errorDetails) {
        this.message = message;
        this.success = success;
        this.errorDetails = errorDetails;
    }

    public String getMessage() {
        return message;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getErrorDetails() {
        return errorDetails;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }
    
    @Override
    public String toString() {
        return "ClientResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", errorDetails='" + (errorDetails != null ? errorDetails : "none") + '\'' +
                '}';
    }
}