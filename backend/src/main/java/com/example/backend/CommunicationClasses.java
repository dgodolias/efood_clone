package com.example.backend;

import java.io.Serializable;
import java.util.List;

public class CommunicationClasses {

    public static class Request implements Serializable {
        // Use a specific serialVersionUID to ensure compatibility with client
        private static final long serialVersionUID = 1234567890123456789L;
        
        private String command;
        private String data;
        
        // Default constructor required for serialization
        public Request() {
            this.command = "";
            this.data = "";
        }

        public Request(String command, String data) {
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
            return "Request{command='" + command + "', data='" + data + "'}";
        }
    }

    public static class Response implements Serializable {
        // Use a specific serialVersionUID to ensure compatibility with client
        private static final long serialVersionUID = 1234567890123456789L;
        
        private String message;
        private boolean success;
        private String errorDetails;
        
        // Default constructor required for serialization
        public Response() {
            this.message = "";
            this.success = false;
            this.errorDetails = null;
        }

        public Response(String message) {
            this.message = message;
            this.success = true;
            this.errorDetails = null;
        }
        
        public Response(String message, boolean success) {
            this.message = message;
            this.success = success;
            this.errorDetails = null;
        }
        
        public Response(String message, boolean success, String errorDetails) {
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
            return "Response{success=" + success + ", message='" + message + "', errorDetails='" 
                    + (errorDetails != null ? errorDetails : "none") + "'}";
        }
    }

    public static class WorkerRequest implements Serializable {
        private String command;
        private String data;

        public WorkerRequest(String command, String data) {
            this.command = command;
            this.data = data;
        }

        public String getCommand() {
            return command;
        }

        public String getData() {
            return data;
        }
    }

    public static class WorkerResponse implements Serializable {
        private String result;

        public WorkerResponse(String result) {
            this.result = result;
        }

        public String getResult() {
            return result;
        }
    }

    public static class ReduceRequest implements Serializable {
        private String command;
        private List<String> mapResults;

        public ReduceRequest(String command, List<String> mapResults) {
            this.command = command;
            this.mapResults = mapResults;
        }

        public String getCommand() {
            return command;
        }

        public List<String> getMapResults() {
            return mapResults;
        }
    }

    public static class ReduceResponse implements Serializable {
        private String result;

        public ReduceResponse(String result) {
            this.result = result;
        }

        public String getResult() {
            return result;
        }
    }
}