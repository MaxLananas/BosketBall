#!/usr/bin/env python3
"""
Lightweight zero-dependency HTTP server bound to 0.0.0.0:8080 for Arena Live Preview.
Serves docs/showcase/index.html at "/" along with /resourcepack/, /releases/, and /docs/.
Permits all Host/Origin headers and iframe embedding.
"""
import http.server
import os
import socketserver
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PORT = int(os.environ.get("PORT", "8080"))


class ShowcaseHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def do_GET(self):
        if self.path in ("/", "/index.html") or self.path.startswith("/?"):
            self.path = "/docs/showcase/index.html"
        return super().do_GET()

    def end_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "no-cache")
        super().end_headers()


class ReusableTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    allow_reuse_address = True


if __name__ == "__main__":
    with ReusableTCPServer(("0.0.0.0", PORT), ShowcaseHandler) as httpd:
        print(f"BosketBall 26.2 Showcase listening on http://0.0.0.0:{PORT}", flush=True)
        httpd.serve_forever()
