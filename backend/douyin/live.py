"""Live stream URL extraction.

Douyin's live web API only carries danmu (chat). The actual video stream is
embedded in the SSR HTML of https://live.douyin.com/{room_id} inside
window._ROUTER_DATA (a big JSON blob). We pull the FLV/HLS pull URLs out of it
so ExoPlayer can play them directly.
"""
import json
import re

import requests

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/117.0"


def _extract_router_data(html: str) -> dict:
    # Try the plain JS assignment first: window._ROUTER_DATA = {...};
    m = re.search(r"_ROUTER_DATA\s*=\s*(\{)", html)
    if m:
        decoder = json.JSONDecoder()
        obj, _ = decoder.raw_decode(html[m.start(1):])
        return obj
    # Fallback: <script id="RENDER_DATA">…</script> is URL-encoded JSON.
    m = re.search(r'id="RENDER_DATA"[^>]*>([^<]+)</script>', html)
    if m:
        from urllib.parse import unquote

        decoded = unquote(m.group(1))
        return json.loads(decoded)
    return {}


def _walk_stream_url(router: dict) -> dict:
    """Drill into the nested loader data to find stream_url."""
    stack = [router]
    while stack:
        cur = stack.pop()
        if isinstance(cur, dict):
            if "stream_url" in cur and isinstance(cur["stream_url"], dict):
                return cur["stream_url"]
            if "flv_pull_url" in cur or "hls_pull_url" in cur:
                return cur
            for v in cur.values():
                if isinstance(v, (dict, list)):
                    stack.append(v)
        elif isinstance(cur, list):
            stack.extend(cur)
    return {}


def get_live(room_id: str, cookie_str: str = "") -> dict:
    """Return {status, title, flv, hls, nickname} for a live room id / share id.

    .. deprecated::
        抖音直播页已不再在 _ROUTER_DATA 里嵌入 stream_url（未登录时为 null），这条
        HTML 抓取路径在当前抖音页面上会返回空的 flv/hls。Android 客户端已改用签名
        的 webcast /enter 端点取流（见 LiveFetcher.parseEnter）。本函数仅留作旧方案
        参考，如需在 Python 侧取流请改为调用签名后的 enter 端点。
    """
    s = requests.Session()
    s.verify = True   # 不要关 TLS 校验，否则 cookie 可能被中间人截获
    headers = {
        "user-agent": UA,
        "accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "accept-language": "zh-CN,zh;q=0.9",
        "referer": "https://live.douyin.com/",
        "upgrade-insecure-requests": "1",
    }
    if cookie_str:
        headers["cookie"] = cookie_str
    resp = s.get(f"https://live.douyin.com/{room_id}", headers=headers, timeout=15)
    router = _extract_router_data(resp.text)

    room = {}
    # walk once to grab room info (title/status/nickname) too
    stack = [router]
    info = {}
    while stack:
        cur = stack.pop()
        if isinstance(cur, dict):
            if "title" in cur and ("status" in cur or "room_status" in cur) and not info:
                info = cur
            for k in ("data", "info", "room", "roomInfo"):
                if k in cur and isinstance(cur[k], dict):
                    stack.append(cur[k])
            for v in cur.values():
                if isinstance(v, (dict, list)):
                    stack.append(v)
        elif isinstance(cur, list):
            stack.extend(cur)

    stream = _walk_stream_url(router)
    flv_map = stream.get("flv_pull_url") or {}
    # prefer highest quality: FULL_HD1 > HD1 > SD1 > _origin > any
    flv_pref = ["FULL_HD1", "ORIGINATION", "BD1", "HD1", "SD1"]
    flv = next((flv_map[k] for k in flv_pref if flv_map.get(k)), "")
    if not flv and flv_map:
        flv = list(flv_map.values())[0]

    status_str = str(info.get("status", ""))
    return {
        "room_id": str(info.get("id_str") or info.get("id") or room_id),
        "title": info.get("title", ""),
        "nickname": (info.get("owner") or {}).get("nickname", ""),
        "status": status_str,  # "2" usually means streaming
        "is_live": status_str in ("2", "1"),
        "flv": flv,
        "hls": stream.get("hls_pull_url") or stream.get("hls_pull_url_map") or "",
        "cover": info.get("cover", {}).get("url_list", [""])[0] if isinstance(info.get("cover"), dict) else "",
    }
