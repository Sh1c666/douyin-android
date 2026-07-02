"""Exercise every read endpoint against the live client and print a compact
report so we know the whole backend works before wiring up the Android side.
"""
import json
import os
import sys
import urllib3
from pathlib import Path

urllib3.disable_warnings()
HERE = Path(__file__).parent
sys.path.insert(0, str(HERE))
from dotenv import load_dotenv

load_dotenv(HERE / ".env")
from douyin.client import DouyinClient  # noqa: E402

hr = json.loads((HERE / "headers_ref.json").read_text(encoding="utf-8"))
cli = DouyinClient(os.environ["DY_COOKIES"], uifid=hr.get("uifid", ""),
                   webid=(hr.get("url_params") or {}).get("webid", ""))


def show(label, ok, detail):
    print(f"[{'OK ' if ok else 'FAIL'}] {label}: {detail}")


# 1) feed
feed = cli.feed(count=20)
show("feed", len(feed) > 0, f"{len(feed)} playable videos")
assert feed, "feed empty"
v = feed[0]
print("    sample:", f"id={v['id']} @{v['author']['nickname']} dur={v['video']['duration']}s")
print("    play_url:", (v["video"]["url"] or "")[:70])
print("    cover:", bool(v["video"]["cover"]), "| music:", v["music"]["title"][:20])

aweme_id = v["id"]
sec_uid = v["author"]["sec_uid"]
print(f"\nUsing aweme_id={aweme_id} sec_uid={sec_uid[:20]}...")

# 2) comments
c = cli.comments(aweme_id)
show("comments", c["status_code"] if "status_code" in c else True,
     f"{len(c['list'])} comments, total={c.get('total')}, has_more={c.get('has_more')}")
if c["list"]:
    print("    top comment:", (c["list"][0]["text"] or "(sticker)")[:40])

# 3) user_info
u = cli.user_info(sec_uid)
show("user_info", "nickname" in u and u.get("nickname"),
     f"@{u.get('nickname')} fans={u.get('follower_count')} works={u.get('aweme_count')}")

# 4) user_posts
up = cli.user_posts(sec_uid)
show("user_posts", len(up["list"]) >= 0, f"{len(up['list'])} works has_more={up['has_more']} cursor={up['max_cursor']}")

# 5) search
sr = cli.search("猫咪", count=10)
show("search", len(sr["list"]) > 0, f"{len(sr['list'])} results")

# 6) aweme_detail
ad = cli.aweme_detail(aweme_id)
show("aweme_detail", bool(ad and ad.get("id")), f"id={ad.get('id') if ad else None}")

print("\n=== backend endpoint sweep done ===")
