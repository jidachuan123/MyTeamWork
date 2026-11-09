---
name: feedback-curl-noproxy
description: 本机 curl 访问 localhost 必须加 --noproxy '*'，否则被 127.0.0.1:7688 代理拦成 502
metadata:
  type: feedback
---

2026-11-09 复核环境确认：`HTTP_PROXY` 与 `HTTPS_PROXY` 都指向 `http://127.0.0.1:7688`。任何对 localhost / 127.0.0.1 的 curl 必须写 `curl --noproxy '*' ...`。

**Why:** 走代理访问本机服务会返回 502，看起来像服务没起来，曾导致误判排查方向。

**How to apply:** 调试接口、探活、触发定时任务时一律加 `--noproxy '*'`；如果报的是连接被拒绝而不是 502，才是服务真的没起。

相关：[[project-env-snapshot-2026-11-09]]
