"""Read-only Douyin web client: builds params, signs with a_bogus, fires GET,
then normalises Douyin's huge nested JSON into a small stable schema the
Android client can consume directly.

The UA below MUST match the UA hardcoded inside sign/dy_ab.js get_ab(),
otherwise a_bogus won't validate.
"""
import random
import string
import urllib.parse as up

import requests

from .sign import Signer

# Must stay identical to the UA passed to get_ab() in sign/dy_ab.js
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/117.0"
BASE = "https://www.douyin.com"

PLATFORM_PARAMS = {
    "device_platform": "webapp",
    "aid": "6383",
    "channel": "channel_pc_web",
    "update_version_code": "170400",
    "pc_client_type": "1",
    "version_code": "170400",
    "version_name": "17.4.0",
    "cookie_enabled": "true",
    "screen_width": "1707",
    "screen_height": "960",
    "browser_language": "zh-CN",
    "browser_platform": "Win32",
    "browser_name": "Edge",
    "browser_version": "125.0.0.0",
    "browser_online": "true",
    "engine_name": "Blink",
    "engine_version": "125.0.0.0",
    "os_name": "Windows",
    "os_version": "10",
    "cpu_core_num": "32",
    "device_memory": "8",
    "platform": "PC",
    "downlink": "10",
    "effective_type": "4g",
    "round_trip_time": "100",
}


def _splice(params: dict) -> str:
    """Join params as k=urlencode(v)&... — exactly the string a_bogus signs."""
    out = ""
    for k, v in params.items():
        out += f"{k}={up.quote(str(v), safe='[]')}&"
    return out[:-1]


def _rand_mstoken(n: int = 128) -> str:
    alphabet = string.ascii_letters + string.digits + "=_"
    return "".join(random.choice(alphabet) for _ in range(n))


def _rand_webid() -> str:
    return "".join(random.choice(string.digits) for _ in range(19))


def _pick_play_url(video: dict) -> str:
    """Choose the URL that actually streams. The direct douyinvod.com URLs in
    play_addr 403; the www.douyin.com/aweme/v1/play/?video_id= redirect works
    (ExoPlayer follows the 302 to a fresh tokenised CDN URL)."""
    pa = (video or {}).get("play_addr") or {}
    urls = pa.get("url_list") or []
    for u in urls:
        if "aweme/v1/play" in u or "/play/?video_id" in u:
            return u
    return urls[0] if urls else ""


def _cover(obj: dict, *keys) -> str:
    cur = obj or {}
    for k in keys:
        cur = cur.get(k) or {}
    return (cur.get("url_list") or [""])[0] if isinstance(cur, dict) else ""


def _int(v) -> int:
    try:
        return int(v)
    except (TypeError, ValueError):
        return 0


def _normalise_aweme(a: dict) -> dict:
    """Douyin aweme -> clean dict. Handles video and image-carousel posts."""
    if not a:
        return None
    video = a.get("video") or {}
    author = a.get("author") or {}
    music = a.get("music") or {}
    stats = a.get("statistics") or a.get("aweme_statistics") or {}

    images = []
    for im in a.get("images") or []:
        # each image has url_list (pick largest-ish last)
        ul = im.get("url_list") or []
        if ul:
            images.append(ul[-1])

    play_url = _pick_play_url(video)
    is_image = bool(images) and not play_url

    return {
        "id": a.get("aweme_id", ""),
        "desc": (a.get("desc") or "").strip(),
        "create_time": a.get("create_time", 0),
        "author": {
            "sec_uid": author.get("sec_uid", ""),
            "nickname": author.get("nickname", ""),
            "avatar": (author.get("avatar_thumb") or {}).get("url_list", [""])[0]
            if author.get("avatar_thumb")
            else "",
            "uid": author.get("uid", ""),
        },
        "video": {
            "url": play_url,
            "cover": _cover(a, "video", "cover"),
            "dynamic_cover": _cover(a, "video", "dynamic_cover"),
            "duration": _int(video.get("duration", 0)) // 1000,  # ms -> s
            "width": _int(video.get("width", 0)),
            "height": _int(video.get("height", 0)),
        },
        "music": {
            "title": music.get("title", ""),
            "author": music.get("author", ""),
            "cover": _cover(a, "music", "cover_thumb") or _cover(a, "music", "cover"),
            "url": ((music.get("play_url") or {}).get("url_list") or [""])[0],
        },
        "stats": {
            "digg": _int(stats.get("digg_count")),
            "comment": _int(stats.get("comment_count")),
            "share": _int(stats.get("share_count")),
            "collect": _int(stats.get("collect_count")),
            "play": _int(stats.get("play_count")),
        },
        "is_image": is_image,
        "images": images,
        "share_url": f"{BASE}/video/{a.get('aweme_id', '')}",
    }


class DouyinClient:
    def __init__(self, cookie_str: str, uifid: str = "", webid: str = "", signer: Signer = None):
        self.cookie = {}
        for part in cookie_str.split("; "):
            if "=" in part:
                k, v = part.split("=", 1)
                self.cookie[k] = v
        self.uifid = uifid
        self.webid = webid or self.cookie.get("webid") or _rand_webid()
        self.verify_fp = self.cookie.get("s_v_web_id", "")
        self.signer = signer or Signer()
        self.s = requests.Session()
        # 校验 TLS：出站请求带着 cookie（含 sessionid），关掉校验会被网络中间人截获。
        # 除非你明确在自签证书的代理后调试，否则保持 True。代理调试时临时设 False。
        self.s.verify = True

    # ---- core signed GET -------------------------------------------------
    def _headers(self, refer: str):
        h = {
            "user-agent": UA,
            "accept": "application/json, text/plain, */*",
            "accept-language": "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6",
            "cache-control": "no-cache",
            "pragma": "no-cache",
            "priority": "u=1, i",
            "referer": refer,
            "sec-ch-ua": '"Microsoft Edge";v="125", "Chromium";v="125", "Not.A/Brand";v="24"',
            "sec-ch-ua-mobile": "?0",
            "sec-ch-ua-platform": '"Windows"',
            "sec-fetch-dest": "empty",
            "sec-fetch-mode": "cors",
            "sec-fetch-site": "same-origin",
        }
        if self.uifid:
            h["uifid"] = self.uifid
        return h

    def _signed_get(self, api: str, params: dict, refer: str, add_vfp: bool = True):
        # a_bogus signs the core query; tail params are appended after.
        query = _splice(params)
        a_bogus = self.signer.get_ab(query, "")
        url = f"{BASE}{api}?{query}&a_bogus={up.quote(a_bogus, safe='')}"
        if add_vfp and self.verify_fp:
            enc = up.quote(self.verify_fp, safe="")
            url += f"&verifyFp={enc}&fp={enc}"
        resp = self.s.get(url, headers=self._headers(refer), cookies=self.cookie, timeout=15)
        try:
            return resp.json()
        except ValueError:
            return {"status_code": -1, "raw": resp.text[:300]}

    # ---- endpoints -------------------------------------------------------
    def feed(self, count: str = "20") -> list:
        """Recommendation feed. tab/feed returns a fresh batch each call with no
        cursor; the caller dedupes by id for infinite scroll."""
        api = "/aweme/v1/web/tab/feed/"
        params = dict(PLATFORM_PARAMS)
        params.update(
            {
                "count": count,
                "feed_style": "1",
                "filter_warn": "0",
                "max_cursor": "0",
                "refresh_cursor": "",
                "refresh_index": "2",
                "tag": "",
                "type": "0",
                "webid": self.webid,
                "msToken": _rand_mstoken(),
            }
        )
        data = self._signed_get(api, params, f"{BASE}/")
        out = []
        for a in data.get("aweme_list") or []:
            n = _normalise_aweme(a)
            if n and (n["video"]["url"] or n["is_image"]):
                out.append(n)
        return out

    def user_info(self, sec_uid: str) -> dict:
        api = "/aweme/v1/web/user/profile/other/"
        params = dict(PLATFORM_PARAMS)
        params.update(
            {
                "publish_video_strategy_type": "2",
                "source": "channel_pc_web",
                "sec_user_id": sec_uid,
                "personal_center_strategy": "1",
                "webid": self.webid,
                "msToken": _rand_mstoken(),
            }
        )
        d = self._signed_get(api, params, f"{BASE}/user/{sec_uid}")
        u = (d.get("user") or {}).get("user") or d.get("user") or {}
        if not u:
            return {"raw": d}
        return {
            "sec_uid": u.get("sec_uid", ""),
            "nickname": u.get("nickname", ""),
            "signature": u.get("signature", ""),
            "avatar": (u.get("avatar_thumb") or {}).get("url_list", [""])[0],
            "uid": u.get("uid", ""),
            "follower_count": _int(u.get("follower_count")),
            "following_count": _int(u.get("following_count")),
            "aweme_count": _int(u.get("aweme_count")),
            "total_favorited": _int(u.get("total_favorited")),
            "is_live": bool((u.get("room_id") or 0)),
            "room_id": str(u.get("room_id") or ""),
        }

    def user_posts(self, sec_uid: str, max_cursor: str = "0", count: str = "18") -> dict:
        api = "/aweme/v1/web/aweme/post/"
        params = dict(PLATFORM_PARAMS)
        params.update(
            {
                "sec_user_id": sec_uid,
                "max_cursor": max_cursor,
                "locate_query": "false",
                "show_live_replay_strategy": "1",
                "need_time_list": "1" if max_cursor == "0" else "0",
                "time_list_query": "0",
                "whale_cut_token": "",
                "cut_version": "1",
                "count": count,
                "publish_video_strategy_type": "2",
                "webid": self.webid,
                "msToken": _rand_mstoken(),
            }
        )
        d = self._signed_get(api, params, f"{BASE}/user/{sec_uid}")
        out = [_normalise_aweme(a) for a in (d.get("aweme_list") or [])]
        return {
            "has_more": d.get("has_more", 0),
            "max_cursor": str(d.get("max_cursor") or "0"),
            "list": [x for x in out if x],
        }

    def comments(self, aweme_id: str, cursor: str = "0", count: str = "20") -> dict:
        api = "/aweme/v1/web/comment/list/"
        params = dict(PLATFORM_PARAMS)
        params.update(
            {
                "aweme_id": aweme_id,
                "cursor": cursor,
                "count": count,
                "item_type": "0",
                "whale_cut_token": "",
                "cut_version": "1",
                "rcFT": "",
                "round_trip_time": "0",
                "webid": self.webid,
                "msToken": _rand_mstoken(),
            }
        )
        d = self._signed_get(api, params, f"{BASE}/video/{aweme_id}")
        out = []
        for c in d.get("comments") or []:
            u = c.get("user") or {}
            out.append(
                {
                    "cid": c.get("cid", ""),
                    "text": c.get("text", ""),
                    "create_time": c.get("create_time", 0),
                    "digg_count": _int(c.get("digg_count")),
                    "reply_comment_total": _int(c.get("reply_comment_total")),
                    "user": {
                        "sec_uid": u.get("sec_uid", ""),
                        "nickname": u.get("nickname", ""),
                        "avatar": (u.get("avatar_thumb") or {}).get("url_list", [""])[0],
                    },
                }
            )
        return {
            "has_more": d.get("has_more", 0),
            "cursor": str(d.get("cursor") or "0"),
            "total": _int(d.get("total")),
            "list": out,
        }

    def comment_replies(self, aweme_id: str, comment_id: str, cursor: str = "0", count: str = "20") -> dict:
        api = "/aweme/v1/web/comment/list/reply/"
        params = dict(PLATFORM_PARAMS)
        params.update(
            {
                "item_id": aweme_id,
                "comment_id": comment_id,
                "cut_version": "1",
                "cursor": cursor,
                "count": count,
                "item_type": "0",
                "round_trip_time": "0",
                "webid": self.webid,
                "msToken": _rand_mstoken(),
            }
        )
        d = self._signed_get(api, params, f"{BASE}/video/{aweme_id}")
        out = []
        for c in d.get("comments") or []:
            u = c.get("user") or {}
            out.append(
                {
                    "cid": c.get("cid", ""),
                    "text": c.get("text", ""),
                    "create_time": c.get("create_time", 0),
                    "digg_count": _int(c.get("digg_count")),
                    "user": {
                        "sec_uid": u.get("sec_uid", ""),
                        "nickname": u.get("nickname", ""),
                        "avatar": (u.get("avatar_thumb") or {}).get("url_list", [""])[0],
                    },
                }
            )
        return {
            "has_more": d.get("has_more", 0),
            "cursor": str(d.get("cursor") or "0"),
            "list": out,
        }

    def search(self, keyword: str, offset: str = "0", count: str = "20") -> dict:
        api = "/aweme/v1/web/general/search/single/"
        params = dict(PLATFORM_PARAMS)
        params.update(
            {
                "search_channel": "aweme_general",
                "enable_history": "1",
                "keyword": keyword,
                "search_source": "tab_search",
                "query_correct_type": "1",
                "is_filter_search": "0",
                "offset": offset,
                "count": count,
                "need_filter_settings": "1" if offset == "0" else "0",
                "list_type": "single",
                "version_code": "190600",
                "version_name": "19.6.0",
                "webid": self.webid,
                "msToken": _rand_mstoken(),
            }
        )
        refer = f"{BASE}/search/{up.quote(keyword)}?type=general"
        d = self._signed_get(api, params, refer)
        # search wraps each hit in {type, aweme_info} inside data[]
        out = []
        for item in d.get("data") or []:
            info = item.get("aweme_info") or item.get("aweme_inline_info") or {}
            n = _normalise_aweme(info)
            if n and (n["video"]["url"] or n["is_image"]):
                out.append(n)
        return {
            "has_more": d.get("has_more", 0),
            "list": out,
        }

    def aweme_detail(self, aweme_id: str) -> dict:
        api = "/aweme/v1/web/aweme/detail/"
        params = dict(PLATFORM_PARAMS)
        params.update(
            {
                "aweme_id": aweme_id,
                "webid": self.webid,
                "msToken": _rand_mstoken(),
            }
        )
        d = self._signed_get(api, params, f"{BASE}/video/{aweme_id}")
        return _normalise_aweme(d.get("aweme_detail") or {})
