#!/bin/bash

# Script to run ManagerConsoleApp.java
# Usage: ./run_manager_console_app.sh [master_host:port]
# Example: ./run_manager_console_app.sh 192.168.1.18:8080

# Navigate to the project root directory
cd "$(dirname "$0")/.."

# Set up classpath - include both backend (for dependencies) and app classes
BACKEND_CLASSPATH=./backend/build/classes/java/main/
APP_JAVA_SRC=./app/src/main/java/
JSON_LIB=./backend/build/libs/

# Check if backend is built
if [ ! -d "$BACKEND_CLASSPATH" ]; then
    echo "Backend classes not found. Building backend..."
    ./gradlew :backend:build
    if [ $? -ne 0 ]; then
        echo "Failed to build backend. Exiting."
        exit 1
    fi
fi

# Create temporary directory for compiled classes
TEMP_CLASSES_DIR="./temp_console_classes"
mkdir -p "$TEMP_CLASSES_DIR"

# Function to clean up on exit
cleanup() {
    echo "Cleaning up temporary files..."
    rm -rf "$TEMP_CLASSES_DIR"
}
trap cleanup EXIT

# Get JSON library jar path
JSON_JAR=$(find ./backend/build/libs/ -name "*.jar" 2>/dev/null | head -1)
if [ -z "$JSON_JAR" ]; then
    # Fallback: look for JSON dependency in Gradle cache
    JSON_JAR=$(find ~/.gradle/caches/ -name "json-*.jar" 2>/dev/null | head -1)
fi

# Build classpath
if [ -n "$JSON_JAR" ]; then
    CLASSPATH="$BACKEND_CLASSPATH:$JSON_JAR:$TEMP_CLASSES_DIR"
else
    # Try to get dependencies through gradle
    echo "Getting dependencies..."
    ./gradlew :backend:dependencies --configuration compileClasspath | grep -o '[^[:space:]]*\.jar' > temp_deps.txt 2>/dev/null
    if [ -s temp_deps.txt ]; then
        DEPS=$(cat temp_deps.txt | tr '\n' ':')
        CLASSPATH="$BACKEND_CLASSPATH:$DEPS:$TEMP_CLASSES_DIR"
        rm -f temp_deps.txt
    else
        echo "Warning: Could not find JSON library. Proceeding with basic classpath."
        CLASSPATH="$BACKEND_CLASSPATH:$TEMP_CLASSES_DIR"
        rm -f temp_deps.txt
    fi
fi

echo "Compiling ManagerConsoleApp..."

# Compile the ManagerConsoleApp
javac -cp "$CLASSPATH" \
      -d "$TEMP_CLASSES_DIR" \
      "$APP_JAVA_SRC/com/example/efood_clone_2/frontend/ManagerConsoleApp.java"

if [ $? -ne 0 ]; then
    echo "Compilation failed. Exiting."
    exit 1
fi

echo "Compilation successful."

# Determine master host and port
MASTER_HOST_PORT=""
if [ $# -eq 1 ]; then
    MASTER_HOST_PORT="$1"
    echo "Using master host: $MASTER_HOST_PORT"
elif [ -n "$MASTER_HOST" ]; then
    MASTER_HOST_PORT="$MASTER_HOST"
    echo "Using master host from environment: $MASTER_HOST_PORT"
else
    echo "Using default master host: localhost:8080"
fi

echo ""
echo "================================================"
echo "Starting Manager Console Application"
echo "================================================"
echo "Available commands:"
echo " - ADD_STORE"
echo " - ADD_PRODUCT" 
echo " - REMOVE_PRODUCT"
echo " - GET_SALES_BY_STORE_TYPE_CATEGORY"
echo " - GET_SALES_BY_PRODUCT_CATEGORY"
echo " - GET_SALES_BY_PRODUCT"
echo " - EXIT"
echo "================================================"
echo ""

# Run the ManagerConsoleApp
if [ -n "$MASTER_HOST_PORT" ]; then
    java -cp "$CLASSPATH" com.example.efood_clone_2.frontend.ManagerConsoleApp "$MASTER_HOST_PORT"
else
    java -cp "$CLASSPATH" com.example.efood_clone_2.frontend.ManagerConsoleApp
fi

echo "Manager Console Application stopped."
