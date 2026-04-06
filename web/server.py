#!/usr/bin/env python3
"""
ELSD Online Compiler — HTTP server
Serves static files from web/ and exposes POST /api/run to execute ELSD code.
"""

import json
import os
import subprocess
import tempfile
from http.server import BaseHTTPRequestHandler, HTTPServer

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEB_DIR = os.path.join(PROJECT_ROOT, "web")
CLASSPATH = os.path.join(PROJECT_ROOT, "build") + ":" + os.path.join(PROJECT_ROOT, "antlr-4.13.2-complete.jar")

import re

SEMANTICS_MARKER = "\u2500\u2500 Running program semantics"


def _parse_java_output(stdout: str, stderr: str, returncode: int) -> dict:
    """Extract token count, parse errors, and semantics section from Java stdout."""
    tokens = 0
    parse_errors = 0
    for line in stdout.splitlines():
        m = re.search(r"Tokens\s*:\s*(\d+)", line)
        if m:
            tokens = int(m.group(1))
        m = re.search(r"Errors\s*:\s*(\d+)", line)
        if m:
            parse_errors = int(m.group(1))

    idx = stdout.find(SEMANTICS_MARKER)
    if idx >= 0:
        # lstrip only newlines so structured-line 2-space indents are preserved
        output = stdout[idx + len(SEMANTICS_MARKER):].lstrip('\n').rstrip()
    else:
        output = stdout.strip()

    return {
        "output": output,
        "stderr": stderr,
        "returncode": returncode,
        "tokens": tokens,
        "parseErrors": parse_errors,
    }


MIME_TYPES = {
    ".html": "text/html",
    ".js":   "application/javascript",
    ".css":  "text/css",
    ".ico":  "image/x-icon",
}


class ELSDHandler(BaseHTTPRequestHandler):

    def log_message(self, fmt, *args):
        print(f"[{self.address_string()}] {fmt % args}")

    # ── Static file serving ──────────────────────────────────────────────────

    def do_GET(self):
        path = self.path.split("?")[0]
        if path == "/":
            path = "/index.html"

        file_path = os.path.join(WEB_DIR, path.lstrip("/"))
        file_path = os.path.normpath(file_path)

        # Prevent path traversal outside web/
        if not file_path.startswith(WEB_DIR):
            self._send_error(403, "Forbidden")
            return

        if not os.path.isfile(file_path):
            self._send_error(404, "Not found")
            return

        ext = os.path.splitext(file_path)[1]
        mime = MIME_TYPES.get(ext, "application/octet-stream")

        with open(file_path, "rb") as f:
            data = f.read()

        self.send_response(200)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    # ── API endpoint ─────────────────────────────────────────────────────────

    def do_POST(self):
        if self.path != "/api/run":
            self._send_error(404, "Not found")
            return

        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length)

        try:
            payload = json.loads(body)
            code = payload.get("code", "")
        except (json.JSONDecodeError, KeyError):
            self._send_error(400, "Invalid JSON")
            return

        result = self._run_elsd(code)
        self._send_json(result)

    def _run_elsd(self, code: str) -> dict:
        with tempfile.NamedTemporaryFile(
            mode="w", suffix=".elsd", delete=False, encoding="utf-8"
        ) as tmp:
            tmp.write(code)
            tmp_path = tmp.name

        try:
            proc = subprocess.run(
                ["java", "-cp", CLASSPATH, "elsd.Main", "--run", tmp_path],
                capture_output=True,
                text=True,
                timeout=10,
                cwd=PROJECT_ROOT,
            )
            return _parse_java_output(proc.stdout, proc.stderr, proc.returncode)
        except subprocess.TimeoutExpired:
            return {
                "output": "", "stderr": "Execution timed out (10 s limit).",
                "returncode": -1, "tokens": 0, "parseErrors": 0,
            }
        except FileNotFoundError:
            return {
                "output": "", "stderr": "Java not found. Make sure Java is on PATH.",
                "returncode": -2, "tokens": 0, "parseErrors": 0,
            }
        finally:
            os.unlink(tmp_path)

    # ── Helpers ───────────────────────────────────────────────────────────────

    def _send_json(self, data: dict):
        body = json.dumps(data).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_error(self, code: int, message: str):
        body = json.dumps({"error": message}).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8080))
    server = HTTPServer(("", port), ELSDHandler)
    print(f"ELSD Online Compiler running at http://localhost:{port}")
    print("Press Ctrl+C to stop.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nServer stopped.")
