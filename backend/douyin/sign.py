"""Persistent Node bridge client for a_bogus signing.

Keeps one `node sign_bridge.js` process alive and talks to it over stdin/stdout
with newline-delimited JSON, so each signature costs only the JS computation
(not a ~200ms node startup).
"""
import json
import subprocess
import threading
from pathlib import Path

SIGN_DIR = Path(__file__).resolve().parent.parent / "sign"
BRIDGE = SIGN_DIR / "sign_bridge.js"


class Signer:
    def __init__(self, node_bin: str = "node"):
        self._node = node_bin
        self._p = None
        self._lock = threading.Lock()

    def _ensure(self):
        if self._p is not None and self._p.poll() is None:
            return
        self._p = subprocess.Popen(
            [self._node, str(BRIDGE)],
            cwd=str(SIGN_DIR),
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding="utf-8",
            bufsize=1,
        )

    def get_ab(self, query: str, data: str = "") -> str:
        """Compute the a_bogus parameter for a (query, data) pair."""
        with self._lock:
            self._ensure()
            assert self._p is not None
            self._p.stdin.write(json.dumps({"op": "ab", "query": query, "data": data}) + "\n")
            self._p.stdin.flush()
            line = self._p.stdout.readline()
            if not line:
                err = self._p.stderr.read() if self._p.stderr else ""
                self._p = None
                raise RuntimeError("sign bridge died: " + err)
            res = json.loads(line)
            if not res.get("ok"):
                raise RuntimeError("sign error: " + res.get("error", ""))
            return res["value"]
