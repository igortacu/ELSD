#!/bin/bash
# start.sh — Build the ELSD Java project (if needed) then launch the web server.

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_ROOT"

# Build the Java project if the build/ directory is missing
if [ ! -d "build" ] || [ ! -f "build/elsd/Main.class" ]; then
    echo "Building ELSD Java project..."
    bash run.sh --generate 2>/dev/null || true
    mkdir -p build
    find src/main/java -name "*.java" > /tmp/elsd_sources.txt
    javac -cp antlr-4.13.2-complete.jar -d build @/tmp/elsd_sources.txt
    echo "Build complete."
fi

echo "Starting ELSD Online Compiler at http://localhost:8080"
python3 web/server.py
