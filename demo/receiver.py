"""Demo HTTP receiver: first event succeeds; second succeeds after two 500s."""

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Handler(BaseHTTPRequestHandler):
    calls = 0

    def do_POST(self):
        payload = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        Handler.calls += 1
        status = 500 if Handler.calls in (2, 3) else 200
        print(f"tentativa={Handler.calls} status={status} payload={payload.decode()}", flush=True)
        body = json.dumps({"received": status == 200}).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    print("Destinatário de demonstração em http://127.0.0.1:8090/webhook", flush=True)
    ThreadingHTTPServer(("127.0.0.1", 8090), Handler).serve_forever()
