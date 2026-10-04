#!/usr/bin/env python3
"""Serves a local pack-build output with HTTP Range support, for debug builds.

    tools/serve-packs.py build-packs/out 8790
    adb reverse tcp:8790 tcp:8790
    ./gradlew installDebug -PdevPacksBase=http://127.0.0.1:8790/

The app then reads packs/catalogue.json (+ .sig) and the pack files from here instead
of the `packs` origin (design §7.11). Python's own http.server ignores Range, which
download resume (DL-4) depends on.
"""
import http.server
import os
import re
import sys

root = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else "build-packs/out")
port = int(sys.argv[2]) if len(sys.argv) > 2 else 8790


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **k):
        super().__init__(*a, directory=root, **k)

    def send_head(self):
        path = self.translate_path(self.path)
        rng = self.headers.get("Range")
        if not rng or not os.path.isfile(path):
            return super().send_head()
        size = os.path.getsize(path)
        m = re.match(r"bytes=(\d+)-(\d*)$", rng)
        if not m:
            return super().send_head()
        start = int(m.group(1))
        end = int(m.group(2)) if m.group(2) else size - 1
        if start >= size:
            self.send_response(416)
            self.send_header("Content-Range", f"bytes */{size}")
            self.end_headers()
            return None
        f = open(path, "rb")
        f.seek(start)
        self.send_response(206)
        self.send_header("Content-Type", self.guess_type(path))
        self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.send_header("Content-Length", str(end - start + 1))
        self.send_header("Accept-Ranges", "bytes")
        self.end_headers()
        return f


print(f"serving {root} on http://127.0.0.1:{port}/")
http.server.ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
