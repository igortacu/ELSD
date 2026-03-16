#!/bin/bash
# ──────────────────────────────────────────────────────────────────────
# ELSD – Build & Run Script
# ──────────────────────────────────────────────────────────────────────
# Usage:
#   ./run.sh examples/sample.elsd            # parse a file
#   ./run.sh --tokens examples/sample.elsd   # also show token stream
#   ./run.sh --ast examples/sample.elsd      # build & print the AST
#   ./run.sh --gui examples/sample.elsd      # open parse-tree GUI
#   ./run.sh --generate                      # regenerate ANTLR sources
# ──────────────────────────────────────────────────────────────────────

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# --- Logging: create logs dir and tee all output to a timestamped file ---
# If ELSD_LOGFILE env var is set it will be used as the logfile path.
# By default capture stdout/stderr to both console and a logfile in logs/
LOG_DIR="logs"
mkdir -p "$LOG_DIR"
if [ -n "$ELSD_LOGFILE" ]; then
    LOGFILE="$ELSD_LOGFILE"
    mkdir -p "$(dirname "$LOGFILE")"
else
    TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
    LOGFILE="$LOG_DIR/run-$TIMESTAMP.log"
fi

echo "Logging to $LOGFILE"
# Redirect all following stdout/stderr through tee (append mode)
exec > >(tee -a "$LOGFILE") 2>&1

ANTLR_JAR="antlr-4.13.2-complete.jar"
SRC_DIR="src/main/java"
GEN_DIR="$SRC_DIR/elsd/generated"
OUT_DIR="build"

# ── Download ANTLR jar if missing ─────────────────────────────────────
if [ ! -f "$ANTLR_JAR" ]; then
    echo "⬇  Downloading ANTLR 4.13.2..."
    curl -O "https://www.antlr.org/download/$ANTLR_JAR"
fi

# ── Regenerate ANTLR sources ─────────────────────────────────────────
generate() {
    echo "🔧 Generating lexer/parser from .g4 grammars..."
    rm -rf "$GEN_DIR"
    mkdir -p "$GEN_DIR"

    java -jar "$ANTLR_JAR" \
        -o "$GEN_DIR" -package elsd.generated -visitor \
        ELSDLexer.g4

    java -jar "$ANTLR_JAR" \
        -o "$GEN_DIR" -package elsd.generated -visitor \
        -lib "$GEN_DIR" \
        ELSDParser.g4

    echo "   ✓ Generated in $GEN_DIR"
}

# ── Handle --generate flag ───────────────────────────────────────────
if [ "$1" == "--generate" ]; then
    generate
    exit 0
fi

# ── Regenerate if generated sources are missing ──────────────────────
if [ ! -f "$GEN_DIR/ELSDLexer.java" ] || [ ! -f "$GEN_DIR/ELSDParser.java" ]; then
    generate
fi

# ── Compile ──────────────────────────────────────────────────────────
echo "🔨 Compiling..."
mkdir -p "$OUT_DIR"
find "$SRC_DIR" -name "*.java" > /tmp/elsd_sources.txt
javac -cp "$ANTLR_JAR" -d "$OUT_DIR" @/tmp/elsd_sources.txt
echo "   ✓ Compiled to $OUT_DIR/"

# ── Run ──────────────────────────────────────────────────────────────
if [ $# -eq 0 ]; then
    echo ""
    echo "Usage: ./run.sh [--tokens] [--gui] <file.elsd>"
    echo ""
    echo "  Try:  ./run.sh examples/sample.elsd"
    exit 0
fi

echo "🚀 Running ELSD parser..."
echo ""
java -cp "$OUT_DIR:$ANTLR_JAR" elsd.Main "$@"
