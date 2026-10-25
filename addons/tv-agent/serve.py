#!/usr/bin/env python3
"""Serve the built Agent APK and its curl installer on this computer.

    python3 addons/tv-agent/serve.py           # then curl the URL it prints
    python3 addons/tv-agent/serve.py --port 8788 --apk <path>

The installer URL works from the TV, from an adb shell, or from another computer on the same
network (including over Tailscale). Nothing is uploaded anywhere; this is a read-only file server
for two files and a checksum.
"""
import argparse
import hashlib
import http.server
import ipaddress
import socket
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
APK_NAME = "tv-agent.apk"


def find_apk(given: str | None) -> Path:
    candidates = []
    if given:
        candidates.append(Path(given))
    candidates += [
        ROOT / "app/build/outputs/apk/release/app-release.apk",
        ROOT.parent.parent / "work/agent" / APK_NAME,
    ]
    for path in candidates:
        if path.is_file():
            return path.resolve()
    sys.exit(
        "No APK found. Build it first:\n"
        "    cd addons/tv-agent && ./gradlew assembleRelease\n"
        "or pass --apk <path>."
    )


def local_addresses() -> list[str]:
    """Best-effort list of addresses a TV could use, including Tailscale's."""
    found: list[str] = []

    def add(value: str) -> None:
        value = value.strip()
        if not value:
            return
        try:
            ip = ipaddress.ip_address(value)
        except ValueError:
            return
        if ip.is_loopback or ip.is_link_local or str(ip) in found:
            return
        found.append(str(ip))

    for tool in (["tailscale", "ip", "-4"], ["hostname", "-I"], ["ipconfig", "getifaddr", "en0"]):
        try:
            out = subprocess.run(tool, capture_output=True, text=True, timeout=3)
        except (OSError, subprocess.SubprocessError):
            continue
        if out.returncode == 0:
            for line in out.stdout.split():
                add(line)

    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            add(info[4][0])
    except OSError:
        pass
    return found


class Handler(http.server.BaseHTTPRequestHandler):
    server_version = "AgentInstall/1.0"
    apk: Path
    digest: str

    def do_HEAD(self) -> None:
        self.route(head=True)

    def do_GET(self) -> None:
        self.route(head=False)

    def route(self, head: bool) -> None:
        path = self.path.split("?", 1)[0]
        if path in ("/", "/index.html"):
            self.send_document(head, "text/html; charset=utf-8", self.page().encode())
        elif path in ("/install", "/install.sh"):
            script = (ROOT / "install.sh").read_text(encoding="utf-8").replace("__BASE__", self.base())
            self.send_document(head, "text/x-shellscript; charset=utf-8", script.encode())
        elif path == f"/{APK_NAME}":
            self.send_apk(head)
        elif path == "/SHA256SUMS":
            self.send_document(head, "text/plain; charset=utf-8",
                               f"{self.digest}  {APK_NAME}\n".encode())
        elif path == "/health":
            self.send_document(head, "text/plain; charset=utf-8", b"ok\n")
        else:
            self.send_document(head, "text/plain; charset=utf-8", b"Not found.\n", status=404)

    def base(self) -> str:
        proto = self.headers.get("X-Forwarded-Proto", "http")
        host = self.headers.get("Host") or f"{self.server.server_address[0]}:{self.server.server_address[1]}"
        return f"{proto}://{host}"

    def page(self) -> str:
        base = self.base()
        size = self.apk.stat().st_size
        return f"""<!doctype html>
<html lang="en"><meta charset="utf-8"><title>Install Agent</title>
<style>
 body {{ background:#0b0f14; color:#e6ebf2; font:16px/1.5 system-ui,sans-serif; max-width:720px;
        margin:6vh auto; padding:0 24px; }}
 h1 {{ color:#9c86ff; letter-spacing:.12em; text-transform:uppercase; }}
 code {{ display:block; background:#121821; border:1px solid #2c3948; border-radius:10px;
         padding:14px 16px; margin:10px 0; overflow-x:auto; }}
 a {{ color:#9c86ff; }}
</style>
<h1>Agent</h1>
<p>{size:,} bytes, SHA-256 <code>{self.digest}</code></p>
<p>On the TV, in a shell (adb, or a terminal app):</p>
<code>curl -fsSL {base}/install | sh</code>
<p>Download only:</p>
<code>curl -fsSL {base}/{APK_NAME} -o /data/local/tmp/{APK_NAME}
pm install -r /data/local/tmp/{APK_NAME}</code>
<p>From a computer in the repo, with <code>TV_HOST</code> set:</p>
<code>curl -fsSL {base}/install | sh</code>
<p><a href="/{APK_NAME}">the APK</a> · <a href="/SHA256SUMS">SHA-256</a> · <a href="/install">install.sh</a></p>
"""

    def send_apk(self, head: bool) -> None:
        self.send_response(200)
        self.send_header("Content-Type", "application/vnd.android.package-archive")
        self.send_header("Content-Length", str(self.apk.stat().st_size))
        self.send_header("Content-Disposition", f'attachment; filename="{APK_NAME}"')
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        if head:
            return
        with self.apk.open("rb") as handle:
            while chunk := handle.read(64 * 1024):
                try:
                    self.wfile.write(chunk)
                except (BrokenPipeError, ConnectionResetError):
                    return

    def send_document(self, head: bool, content_type: str, body: bytes, status: int = 200) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        if not head:
            self.wfile.write(body)

    def log_message(self, fmt: str, *args) -> None:
        print(f"{self.address_string()} {fmt % args}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Serve the Agent APK and its curl installer.")
    parser.add_argument("--apk", help="path to the release APK")
    parser.add_argument("--port", type=int, default=8788)
    parser.add_argument("--bind", default="0.0.0.0", help="address to bind (default: all)")
    args = parser.parse_args()

    Handler.apk = find_apk(args.apk)
    Handler.digest = hashlib.sha256(Handler.apk.read_bytes()).hexdigest()
    server = http.server.ThreadingHTTPServer((args.bind, args.port), Handler)
    port = server.server_address[1]

    print(f"Agent APK: {Handler.apk} ({Handler.apk.stat().st_size:,} bytes)", flush=True)
    print(f"SHA-256:   {Handler.digest}", flush=True)
    print("Install with:", flush=True)
    for address in local_addresses() or ["<this-computer>"]:
        print(f"    curl -fsSL http://{address}:{port}/install | sh", flush=True)
    print("Ctrl-C to stop.", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print()


if __name__ == "__main__":
    main()
