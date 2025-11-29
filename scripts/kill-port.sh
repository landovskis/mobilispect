#!/bin/bash

# Script to kill processes running on a specified port
# Usage: ./scripts/kill-port.sh <port>

set -e

# Check if port argument is provided
if [ -z "$1" ]; then
    echo "Error: Port number is required"
    echo "Usage: $0 <port>"
    exit 1
fi

PORT=$1

# Validate port number
if ! [[ "$PORT" =~ ^[0-9]+$ ]] || [ "$PORT" -lt 1 ] || [ "$PORT" -gt 65535 ]; then
    echo "Error: Invalid port number. Must be between 1 and 65535"
    exit 1
fi

echo "Searching for processes on port $PORT..."

# Find PIDs using the port (works on macOS and Linux)
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    PIDS=$(lsof -ti:$PORT 2>/dev/null || true)
else
    # Linux
    PIDS=$(lsof -ti:$PORT 2>/dev/null || fuser $PORT/tcp 2>/dev/null || true)
fi

if [ -z "$PIDS" ]; then
    echo "No processes found running on port $PORT"
    exit 0
fi

echo "Found processes: $PIDS"

# Display process information before killing
echo ""
echo "Process details:"
if [[ "$OSTYPE" == "darwin"* ]]; then
    lsof -i:$PORT
else
    lsof -i:$PORT 2>/dev/null || netstat -tlnp | grep ":$PORT"
fi

echo ""
read -p "Kill these processes? (y/n) " -n 1 -r
echo ""

if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "Killing processes..."
    for PID in $PIDS; do
        kill -9 $PID 2>/dev/null && echo "Killed process $PID" || echo "Failed to kill process $PID"
    done
    echo "Done!"
else
    echo "Aborted."
    exit 0
fi
