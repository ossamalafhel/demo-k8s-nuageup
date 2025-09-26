#!/bin/bash
set -e

# Build script for Banking Demo application

echo "Building Banking Demo application..."

# Check Java version
if ! java -version 2>&1 | grep -q "version \"17"; then
    echo "Error: Java 17 is required"
    exit 1
fi

# Clean and build
mvn clean package

echo "Build completed successfully!"
echo "JAR file: target/banking-demo-*.jar"