#!/bin/bash
# Starts the ELSD Online Compiler (React frontend + Python API backend)

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_ROOT"

# ── Build Java if needed ──────────────────────────────────────────────────────
if [ ! -d "build" ] || [ ! -f "build/elsd/Main.class" ]; then
    echo "Building ELSD Java project..."
    bash run.sh --generate 2>/dev/null || true
    mkdir -p build
    find src/main/java -name "*.java" > /tmp/elsd_sources.txt
    javac -cp antlr-4.13.2-complete.jar -d build @/tmp/elsd_sources.txt
    echo "Build complete."
fi

# ── Install npm dependencies if needed ───────────────────────────────────────
if [ ! -d "web-react/node_modules" ]; then
    echo "Installing frontend dependencies..."
    cd web-react && npm install && cd ..
fi

# ── Start Python API server in background ────────────────────────────────────
echo "Starting API server on http://localhost:8080 ..."
python3 web/server.py &
API_PID=$!

# Give the server a moment to start
sleep 1

# ── Start Vite dev server (foreground) ───────────────────────────────────────
echo "Starting frontend on http://localhost:5173 ..."
echo ""
echo "  Open: http://localhost:5173"
echo "  Stop: Ctrl+C"
echo ""

cd web-react
npm run dev

# ── Cleanup on exit ───────────────────────────────────────────────────────────
kill $API_PID 2>/dev/null || true
