# 刷刷 — 自用抖音只读客户端（自包含，无需后端）

一个**只读**的抖音 Android 客户端：刷推荐流 / 搜视频 / 看评论 / 看主页，**无任何互动**。**App 直接调抖音官方接口，签名在端上做，不依赖任何本地后端**。装上 APK、粘一次 cookie 就能用。

> ⚠️ 仅限个人低频使用。技术上违反抖音服务条款，**勿公开传播、勿商用**，建议小号。

---

## 快速开始（三步）

### 1. 构建 APK
本机没有 Android SDK，**用 Android Studio 构建**：
1. Android Studio → **Open** → 选 `android/` 文件夹。
2. 等 Gradle Sync（首次下载 SDK + 依赖，需几分钟和好网络）。
3. **Run ▶** 选真机/模拟器，或 Build → APK。

> Sync 报 Java 版本错误？`File → Settings → Build Tools → Gradle`，把 **Gradle JDK** 设为 **Android Studio JDK / jbr**（系统装的 Java 25 会让命令行 Gradle 挂，别用命令行构建）。

### 2. 粘 cookie
1. 浏览器（已登录抖音）打开 https://www.douyin.com → F12 → Network → 任选一个 `douyin.com` 请求 → Headers 里复制整段 **Cookie**。
2. App 底部 **设置** → 粘进 Cookie 输入框（或点「粘贴」按钮）→ **保存**。
3. 点 **测试**，应显示 `✓ 签名+cookie 正常，取到 N 条视频`。

### 3. 用
- **首页**：上下滑刷推荐流。右上喇叭静音。点头像进主页；点评论看评论（可展开二级回复）。
- **搜索**：底部「搜索」tab → 输入关键词 → 三列封面网格，点封面全屏顺序播放（可下滑翻页）。
- **主页**：头像/数据/作品九宫格，点作品全屏播放。

---

## 工作原理（核心创新）

**签名搬到了端上**：抖音接口要 `a_bogus` 签名，算法是 1.1 万行混淆 JS（`dy_ab.js`，来自 cv-cat/DouYin_Spider）。这个签名 JS 在 Node 里跑过没问题，但放进 App 有两个坑被绕过了：

1. `get_ab`（a_bogus 生成器）其实**不依赖 jsrsasign**——jsrsasign 只服务 bd-ticket-guard（我们只读用不到）。验证过：删掉 `require('jsrsasign')` 后 `get_ab` 照常产出有效签名。`tools/make_dy_ab_web.py` 就是干这事的，产物 `android/app/src/main/assets/dy_ab_web.js`（已剥 jsrsasign + IIFE 包裹避开浏览器全局名冲突）。
2. 这段 JS 在**隐藏 WebView**（Chromium V8）里跑，App 调 `window._dyGetAb(query, data)` 拿签名。WebView 全程保活，每次签名只花几毫秒。

请求流：Retrofit `@QueryMap` 构造参数 → `DouyinSignInterceptor` 读取 OkHttp 已编码的 query、找 WebView 签 a_bogus、追加 `a_bogus/verifyFp/fp` + UA/referer/cookie → 发出。**签的就是要发的**，和原来后端的等价性一致。

---

## 已知限制

| 项 | 状况 | 应对 |
|---|---|---|
| **搜索** | 走签名 `/aweme/v1/web/general/search/single/`（参数对齐 cv-cat/DouYin_Spider）。**该端点常被抖音 `verify_check` 风控返空**——能搜到是运气，搜不到界面会显示原因（如「搜索被拒 status_code=…」=风控） | 真机实测；若长期空，换词/换时段或接受此限制。刷视频/评论/主页不受影响 |
| **cookie 过期** | 几天~两周，表现为首页空/报错 | 重新粘 cookie → 保存 |
| **推荐流每批较少** | tab_feed 每次约 4~6 条真视频（无游标，靠去重无限流） | 自动预取+去重，滑动无感 |
| **签名算法变更** | 抖音偶尔改 a_bogus，会全挂 | 重跑 `tools/make_dy_ab_web.py`（需先更新 `backend/sign/dy_ab.js`） |
| **只读** | 无点赞/评论/转发/私信/发作品 | 设计如此 |

---

## 排错

| 现象 | 解决 |
|---|---|
| 首页空 / 报错 | cookie 过期 → 设置里重新粘 cookie |
| 测试报"能连上但没取到视频" | cookie 过期或未登录（没 sessionid） |
| Gradle 报 Java 版本 | 用 Android Studio 自带 JDK（见上） |
| 搜索结果空 | 多半被抖音风控（`verify_check`）；界面会显示原因。可换词/换时段重试，或 `adb logcat` 看请求 |
| 首次进入首页加载慢 | 隐藏 WebView 首次初始化签名引擎（~1秒），之后秒签 |

---

## `backend/` 文件夹是什么？（可选，高级）

这是**早期版本的本地后端**（Python FastAPI），现在 App 已经不需要它了。保留它的原因：

- **多设备局域网共享**：想在多个设备用、又不想每个都粘 cookie，可以让一台机器跑后端，但需要改回旧架构（本仓库的客户端已切到无后端直连，要切回需自行改 `data/` 层）。
- **签名调试参考**：`backend/douyin/client.py` 是端上 `DouyinParams`+`Mapper` 的 Python 原型；`backend/sign/dy_ab.js` 是签名 JS 的源头，更新 a_bogus 时从这里取新版。

正常使用**完全不用碰 `backend/`**。如果碍眼可以删掉，App 不依赖它。

---

## 更新签名（a_bogus 全挂时，几个月一次）

```bash
# 1. 取新版 dy_ab.js（从 cv-cat/DouYin_Spider 的 static/dy_ab.js）覆盖 backend/sign/dy_ab.js
# 2. 重新生成端上用的版本：
python tools/make_dy_ab_web.py
# 3. 用 Node 验证一下能签名：
node -e "const vm=require('vm'),fs=require('fs');const c=fs.readFileSync('android/app/src/main/assets/dy_ab_web.js','utf8');const s={window:{},performance:{now:()=>Date.now()},Date,Math};s.globalThis=s;vm.createContext(s);vm.runInContext(c,s);console.log('ab len:',s.window._dyGetAb('a=1','').length)"
# 4. 重新构建 APK
```
