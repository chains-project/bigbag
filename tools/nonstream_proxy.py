#!/usr/bin/env python3
"""
nonstream_proxy.py — make an OpenAI-compatible endpoint with broken streaming usable by opencode.

Some providers (e.g. superleanai) drop `delta.tool_calls[].function.arguments` when
`stream: true`, so the agent receives every tool call with empty arguments. Their
non-streaming responses are correct. This proxy:

  * receives POST /v1/chat/completions from opencode,
  * forwards it upstream with `stream: false`,
  * replays the complete answer to the client as an OpenAI-style SSE stream
    (role/content/tool_calls chunk, finish_reason chunk, optional usage chunk, [DONE]).

Every other request is passed through unchanged. The client's Authorization header is
forwarded as-is: the proxy never stores an API key.

Usage (bind to the docker0 address so only local containers can reach it):
    python3 tools/nonstream_proxy.py --upstream http://superleanai.com:8082/v1 \
        --host 172.17.0.1 --port 18082
Then set LLM_BASE_URL=http://172.17.0.1:18082/v1 in the .env.
"""
import argparse
import json
import sys
import threading
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

UPSTREAM = ""
TIMEOUT = 900
_lock = threading.Lock()
_stats = {"requests": 0, "streamed": 0, "errors": 0}


def log(msg: str) -> None:
    with _lock:
        print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):  # silence default access log
        pass

    def _forward(self, method: str, body: bytes | None):
        url = UPSTREAM + self.path[len("/v1"):] if self.path.startswith("/v1") else UPSTREAM + self.path
        headers = {k: v for k, v in self.headers.items()
                   if k.lower() in ("authorization", "content-type", "accept")}
        req = urllib.request.Request(url, data=body, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
                return r.status, r.headers.get("Content-Type", "application/json"), r.read()
        except urllib.error.HTTPError as e:
            return e.code, e.headers.get("Content-Type", "application/json"), e.read()

    def _send(self, status: int, ctype: str, payload: bytes):
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        status, ctype, payload = self._forward("GET", None)
        self._send(status, ctype, payload)

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0) or 0))
        with _lock:
            _stats["requests"] += 1
        if not self.path.rstrip("/").endswith("/chat/completions"):
            status, ctype, payload = self._forward("POST", body)
            return self._send(status, ctype, payload)

        try:
            req = json.loads(body or b"{}")
        except json.JSONDecodeError:
            status, ctype, payload = self._forward("POST", body)
            return self._send(status, ctype, payload)

        wants_stream = bool(req.get("stream"))
        include_usage = bool((req.get("stream_options") or {}).get("include_usage"))
        req["stream"] = False
        req.pop("stream_options", None)
        status, ctype, payload = self._forward("POST", json.dumps(req).encode())

        if not wants_stream or status != 200:
            if status != 200:
                with _lock:
                    _stats["errors"] += 1
                log(f"upstream {status}: {payload[:200]!r}")
            return self._send(status, ctype, payload)

        try:
            resp = json.loads(payload)
        except json.JSONDecodeError:
            with _lock:
                _stats["errors"] += 1
            return self._send(502, "application/json",
                              json.dumps({"error": {"message": "upstream returned non-JSON"}}).encode())

        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()
        for chunk in to_chunks(resp, include_usage):
            self.wfile.write(b"data: " + json.dumps(chunk).encode() + b"\n\n")
        self.wfile.write(b"data: [DONE]\n\n")
        self.wfile.flush()
        self.close_connection = True
        with _lock:
            _stats["streamed"] += 1
            n = _stats["streamed"]
        if n % 25 == 0:
            log(f"stats {_stats}")


def to_chunks(resp: dict, include_usage: bool) -> list[dict]:
    """Turn a complete chat.completion into chat.completion.chunk objects."""
    base = {"id": resp.get("id", "chatcmpl-proxy"), "object": "chat.completion.chunk",
            "created": resp.get("created", int(time.time())), "model": resp.get("model", "")}
    chunks = []
    for choice in resp.get("choices", []):
        msg = choice.get("message") or {}
        delta = {"role": msg.get("role", "assistant")}
        if msg.get("content"):
            delta["content"] = msg["content"]
        for key in ("reasoning_content", "reasoning"):
            if msg.get(key):
                delta[key] = msg[key]
        if msg.get("tool_calls"):
            delta["tool_calls"] = [
                {"index": i, "id": tc.get("id"), "type": tc.get("type", "function"),
                 "function": {"name": tc["function"].get("name"),
                              "arguments": tc["function"].get("arguments") or "{}"}}
                for i, tc in enumerate(msg["tool_calls"])]
        idx = choice.get("index", 0)
        chunks.append({**base, "choices": [{"index": idx, "delta": delta, "finish_reason": None}]})
        chunks.append({**base, "choices": [{"index": idx, "delta": {},
                                            "finish_reason": choice.get("finish_reason", "stop")}]})
    if include_usage and resp.get("usage"):
        chunks.append({**base, "choices": [], "usage": resp["usage"]})
    return chunks


def main() -> None:
    global UPSTREAM, TIMEOUT
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--upstream", required=True, help="upstream base URL, e.g. http://host:port/v1")
    ap.add_argument("--host", default="172.17.0.1", help="bind address (default: docker0)")
    ap.add_argument("--port", type=int, default=18082)
    ap.add_argument("--timeout", type=int, default=900)
    args = ap.parse_args()
    UPSTREAM, TIMEOUT = args.upstream.rstrip("/"), args.timeout
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    log(f"non-stream proxy on http://{args.host}:{args.port}/v1 -> {UPSTREAM}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    log(f"final stats {_stats}")
    sys.exit(0)


if __name__ == "__main__":
    main()
