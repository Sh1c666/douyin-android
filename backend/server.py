"""FastAPI server exposing the read-only Douyin client as clean JSON.

Run:  uvicorn server:app --host 0.0.0.0 --port 8000
The Android client talks only to this server — no signing, no cookies, no risk
control on the device side.
"""
import json
import os
import sys
from pathlib import Path

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

HERE = Path(__file__).parent
sys.path.insert(0, str(HERE))
load_dotenv(HERE / ".env")

from douyin.client import DouyinClient  # noqa: E402
from douyin.live import get_live  # noqa: E402

COOKIE = os.environ.get("DY_COOKIES", "")
_headers_ref = {}
_hr_path = HERE / "headers_ref.json"
if _hr_path.exists():
    _headers_ref = json.loads(_hr_path.read_text(encoding="utf-8"))

client = DouyinClient(
    cookie_str=COOKIE,
    uifid=_headers_ref.get("uifid", ""),
    webid=(_headers_ref.get("url_params") or {}).get("webid", ""),
)

app = FastAPI(title="Douyin Read-Only Backend", version="1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/api/health")
def health():
    return {
        "ok": True,
        "logged_in": bool(client.cookie.get("sessionid")),
        "has_cookie": bool(COOKIE),
        "webid": client.webid,
        "verify_fp": bool(client.verify_fp),
    }


@app.get("/api/feed")
def feed(count: int = Query(20, ge=1, le=40)):
    """Recommendation feed. Returns a fresh batch (dedupe client-side)."""
    try:
        return JSONResponse(client.feed(count=str(count)))
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"feed failed: {e}")


@app.get("/api/aweme/{aweme_id}")
def aweme_detail(aweme_id: str):
    try:
        return JSONResponse(client.aweme_detail(aweme_id))
    except Exception as e:
        raise HTTPException(status_code=502, detail=str(e))


@app.get("/api/user/{sec_uid}")
def user_info(sec_uid: str):
    try:
        return JSONResponse(client.user_info(sec_uid))
    except Exception as e:
        raise HTTPException(status_code=502, detail=str(e))


@app.get("/api/user/{sec_uid}/works")
def user_works(sec_uid: str, cursor: str = "0", count: str = "18"):
    try:
        return JSONResponse(client.user_posts(sec_uid, max_cursor=cursor, count=count))
    except Exception as e:
        raise HTTPException(status_code=502, detail=str(e))


@app.get("/api/comments/{aweme_id}")
def comments(aweme_id: str, cursor: str = "0", count: str = "20"):
    try:
        return JSONResponse(client.comments(aweme_id, cursor=cursor, count=count))
    except Exception as e:
        raise HTTPException(status_code=502, detail=str(e))


@app.get("/api/comments/{aweme_id}/{comment_id}/replies")
def comment_replies(aweme_id: str, comment_id: str, cursor: str = "0", count: str = "20"):
    try:
        return JSONResponse(client.comment_replies(aweme_id, comment_id, cursor=cursor, count=count))
    except Exception as e:
        raise HTTPException(status_code=502, detail=str(e))


@app.get("/api/search")
def search(keyword: str, offset: str = "0", count: str = "20"):
    try:
        return JSONResponse(client.search(keyword, offset=offset, count=count))
    except Exception as e:
        raise HTTPException(status_code=502, detail=str(e))


@app.get("/api/live/{room_id}")
def live(room_id: str):
    try:
        return JSONResponse(get_live(room_id, cookie_str=COOKIE))
    except Exception as e:
        raise HTTPException(status_code=502, detail=str(e))


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
