#!/bin/sh
set -e

# Docker entrypoint script
echo "Starting Banking Demo application..."

# JVM memory settings based on container limits
if [ -n "$JAVA_OPTS" ]; then
    echo "Using JAVA_OPTS: $JAVA_OPTS"
else
    # Auto-configure based on container memory
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"
fi

# Wait for database if URL provided
if [ -n "$DB_URL" ]; then
    echo "Waiting for database connection..."
    sleep 5
fi

# Start application
exec java $JAVA_OPTS \
    -Djava.security.egd=file:/dev/./urandom \
    -jar /app/app.jar