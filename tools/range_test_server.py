#!/usr/bin/env python3
"""
Motrix Lite 本地测试服务器：支持 HTTP Range / Content-Disposition / ETag，
用于在真机上验证多线程分段下载、暂停恢复、断点续传。

用法:
  python tools/range_test_server.py --port 8000 --size-mb 50
  python tools/range_test_server.py --port 8000 --file D:\test.iso --throttle-kbps 200

然后用手机浏览器或 Motrix Lite 访问（同一局域网）:
  http://<电脑IP>:8000/testfile.bin
或 adb reverse 后访问:
  adb reverse tcp:8000 tcp:8000
  http://127.0.0.1:8000/testfile.bin
"""
import argparse
import os
import re
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

FILE_PATH = None
THROTTLE_BPS = 0


class RangeHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_HEAD(self):
        self._serve(head_only=True)

    def do_GET(self):
        self._serve(head_only=False)

    def log_message(self, fmt, *args):
        print("[server] " + fmt % args)

    def _serve(self, head_only=False):
        size = os.path.getsize(FILE_PATH)
        range_header = self.headers.get("Range")
        start, end = 0, size - 1
        status = 200
        if range_header:
            match = re.match(r"bytes=(\d+)-(\d*)", range_header)
            if match:
                start = int(match.group(1))
                if start >= size:
                    self.send_response(416)
                    self.send_header("Content-Range", f"bytes */{size}")
                    self.send_header("Content-Length", "0")
                    self.end_headers()
                    return
                end = int(match.group(2)) if match.group(2) else size - 1
                end = min(end, size - 1)
                status = 206
        length = end - start + 1
        self.send_response(status)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(length))
        self.send_header("Content-Disposition", 'attachment; filename="testfile.bin"')
        self.send_header("ETag", '"range-test-etag"')
        self.send_header("Last-Modified", "Mon, 01 Jan 2024 00:00:00 GMT")
        if status == 206:
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.end_headers()
        if head_only:
            return
        remaining = length
        with open(FILE_PATH, "rb") as handle:
            handle.seek(start)
            while remaining > 0:
                chunk = handle.read(min(64 * 1024, remaining))
                if not chunk:
                    break
                self.wfile.write(chunk)
                remaining -= len(chunk)
                if THROTTLE_BPS > 0:
                    time.sleep(len(chunk) / THROTTLE_BPS)


def main():
    global FILE_PATH, THROTTLE_BPS
    parser = argparse.ArgumentParser(description="Motrix Lite range test server")
    parser.add_argument("--port", type=int, default=8000)
    parser.add_argument("--file", type=str, default=None)
    parser.add_argument("--size-mb", type=int, default=20)
    parser.add_argument("--throttle-kbps", type=int, default=0, help="限速 KB/s，0 表示不限速")
    args = parser.parse_args()

    if args.file:
        FILE_PATH = args.file
        if not os.path.isfile(FILE_PATH):
            raise SystemExit(f"file not found: {FILE_PATH}")
    else:
        FILE_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "testfile.bin")
        if not os.path.exists(FILE_PATH):
            print(f"creating test file {FILE_PATH} ({args.size_mb} MB) ...")
            with open(FILE_PATH, "wb") as handle:
                for i in range(args.size_mb * 16):
                    handle.write(bytes((i + j) % 256 for j in range(64 * 1024)))

    THROTTLE_BPS = args.throttle_kbps * 1024 if args.throttle_kbps > 0 else 0
    server = ThreadingHTTPServer(("0.0.0.0", args.port), RangeHandler)
    size = os.path.getsize(FILE_PATH)
    print(f"serving {FILE_PATH} ({size / 1024 / 1024:.1f} MB) at http://0.0.0.0:{args.port}/testfile.bin")
    print("Range supported, Content-Disposition enabled, ETag enabled")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        server.shutdown()


if __name__ == "__main__":
    main()