"""Stage-0 gate test: does cookie + a_bogus actually return feed videos?

Exit 0 with a printed summary => the whole project is viable.
Any other output => stop and fix before touching the client.
"""
import json
import os
import sys
from pathlib import Path

import urllib3
from dotenv import load_dotenv

urllib3.disable_warnings()

HERE = Path(__file__).parent
sys.path.insert(0, str(HERE))
load_dotenv(HERE / ".env")

from douyin.client import DouyinClient  # noqa: E402

cookie = os.environ["DY_COOKIES"]
headers_ref = json.loads((HERE / "headers_ref.json").read_text(encoding="utf-8"))

cli = DouyinClient(
    cookie_str=cookie,
    uifid=headers_ref.get("uifid", ""),
    webid=headers_ref["url_params"].get("webid", ""),
)

print("webid    :", cli.webid)
print("verifyFp :", cli.verify_fp)
print("uifid    :", (cli.uifid[:24] + "...") if cli.uifid else "(none)")
print("sessionid:", "YES" if cli.cookie.get("sessionid") else "NO")
print("=" * 60)

data = cli.feed()
print("top-level keys:", list(data.keys()))

# Douyin returns {"status_code":0, "aweme_list":[...]} on module/feed,
# sometimes wrapped. Dig for aweme_list.
awemes = data.get("aweme_list") or data.get("aweme_detail") or []
if not awemes and isinstance(data.get("data"), dict):
    awemes = data["data"].get("aweme_list", [])

print("status_code   :", data.get("status_code"))
print("aweme count   :", len(awemes) if isinstance(awemes, list) else "N/A")

if isinstance(awemes, list) and awemes:
    a = awemes[0]
    desc = (a.get("desc") or "").strip().replace("\n", " ")[:40]
    author = (a.get("author") or {}).get("nickname", "?")
    # find a playable url
    play = ((a.get("video") or {}).get("play_addr") or {}).get("url_list") or []
    print(f"  [0] @{author} | {desc}")
    print(f"      play_url: {play[0][:70] + '...' if play else '(none)'}")
    print("\n*** STAGE 0 PASSED: real feed data returned ***")
else:
    print("\n!!! STAGE 0 FAILED: no aweme_list. Raw response (first 600 chars):")
    print(json.dumps(data, ensure_ascii=False)[:600])
    sys.exit(1)
