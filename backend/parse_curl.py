"""Parse the captured curl command into config the backend can use.

Reads sample_curl.sh (a verbatim "Copy as cURL" from the browser), pulls out
the cookie, headers and URL via shlex, writes backend/.env and a small
headers.json. This avoids hand-transcribing a 2KB cookie.
"""
import json
import shlex
import urllib.parse as up
from pathlib import Path

HERE = Path(__file__).parent
raw = (HERE / "sample_curl.sh").read_text(encoding="utf-8")
# join shell line-continuations (backslash + newline) before tokenising
raw = raw.replace("\\\n", " ")
tokens = shlex.split(raw, posix=True)

url = ""
cookie = ""
headers = {}
i = 0
while i < len(tokens):
    t = tokens[i]
    if t == "curl":
        i += 1
        continue
    if t.startswith("http"):
        url = t
        i += 1
        continue
    if t == "-H" and i + 1 < len(tokens):
        h = tokens[i + 1]
        i += 2
        if ":" in h:
            k, v = h.split(":", 1)
            headers[k.strip().lower()] = v.strip()
        continue
    if t in ("-b", "--cookie") and i + 1 < len(tokens):
        cookie = tokens[i + 1]
        i += 2
        continue
    if t.startswith("-"):
        i += 1
        continue
    if not url:
        url = t
    i += 1

# cookie -> dict for inspection
cookie_dict = {}
for part in cookie.split("; "):
    if "=" in part:
        k, v = part.split("=", 1)
        cookie_dict[k] = v

url_params = dict(up.parse_qsl(up.urlsplit(url).query, keep_blank_values=True))

print("URL host+path:", up.urlsplit(url).scheme, up.urlsplit(url).netloc, up.urlsplit(url).path)
print("headers keys :", sorted(headers.keys()))
print("cookie count :", len(cookie_dict))
need = ["s_v_web_id", "msToken", "ttwid", "sessionid", "uifid", "passport_csrf_token"]
print("key cookies present:")
for k in need:
    v = cookie_dict.get(k)
    print(f"  {k:22}: {'YES ('+str(len(v))+' chars)' if v else 'MISSING'}")
print("URL verifyFp  :", url_params.get("verifyFp"))
print("URL webid     :", url_params.get("webid"))
print("URL uifid len :", len(url_params.get("uifid", "")))
print("URL msToken len:", len(url_params.get("msToken", "")))

# Write .env (gitignored) and a headers reference
env = (HERE / ".env")
env.write_text(
    "# Auto-generated from sample_curl.sh by parse_curl.py\n"
    "# DO NOT COMMIT. Refresh by re-pasting your curl into sample_curl.sh\n"
    "# and running: python parse_curl.py\n"
    f"DY_COOKIES={cookie!r}\n",
    encoding="utf-8",
)
(HERE / "headers_ref.json").write_text(
    json.dumps(
        {
            "user-agent": headers.get("user-agent", ""),
            "uifid": headers.get("uifid", ""),
            "referer": headers.get("referer", ""),
            "url_params": {
                "webid": url_params.get("webid", ""),
                "uifid": url_params.get("uifid", ""),
                "verifyFp": url_params.get("verifyFp", ""),
            },
        },
        ensure_ascii=False,
        indent=2,
    ),
    encoding="utf-8",
)
print("\nWrote", env)
print("Wrote", HERE / "headers_ref.json")
