import re, pathlib, sys
ROOT = pathlib.Path(__file__).resolve().parent.parent
src = (ROOT/'backend/sign/dy_ab.js').read_text(encoding='utf-8')
body = re.sub(r"const\s+jsrsasign\s*=\s*require\(['\"]jsrsasign['\"]\)\s*;", "const jsrsasign = {};", src)
body = re.sub(r"module\.exports\s*=\s*\{[^}]*\}\s*;?\s*$", "", body).rstrip()
wrapped = ("/* AUTO-GENERATED. Standalone a_bogus signer for the WebView. */\n"
  "var __W = (typeof window !== 'undefined') ? window : globalThis;\n"
  "(function () {\n" + body + "\n  __W._dyGetAb = get_ab;\n})();\n")
(ROOT/'android/app/src/main/assets/dy_ab_web.js').write_text(wrapped, encoding='utf-8')
print("wrote android/app/src/main/assets/dy_ab_web.js")
