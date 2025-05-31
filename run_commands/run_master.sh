#!/bin/bash

# Script to run Master.class

# Navigate to the project root directory
cd "$(dirname "$0")/.."

# Set the classpath to include the backend directory
CLASSPATH=./run_commands/

# Run the Master class
echo "Starting Master server..."
java -cp $CLASSPATH com.example.backend.Master

echo "Master server stopped."

