package com.example.backend;

import java.io.Serializable;
import java.util.List;

public class CommunicationClasses {

    public static class Request implements Serializable {
        private String command;
        private String data;

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
    }

    public static class Response implements Serializable {
        private String message;

        public Response(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
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