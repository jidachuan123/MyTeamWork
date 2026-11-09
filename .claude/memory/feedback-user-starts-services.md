---
name: feedback-user-starts-services
description: 服务由用户自己启动，AI 不要代启常驻服务，只能起临时端口短命实例并立即杀掉
metadata:
  type: feedback
---

用户明确说过（2026-10-16）：「你不用帮我启动」。eureka / provider / consumer 与各前端常驻进程都由用户自己用桌面 bat 启动。

**Why:** 本机长期跑着多套服务，AI 另起实例会抢端口、抢 headless 浏览器、写同一个 `target/report` 输出目录；而且用户自己清楚当前生效的是哪份配置（外部 `provider-service/config/application.yml`）。

**How to apply:** 需要验证时只起临时端口的短命实例（显式 `--server.port`），用完立刻杀掉；不要调用 `00-start-all.bat`；不要重启用户已在跑的进程，除非用户明确要求。杀实例后立刻重编译可能报「写入 .class 出错」，重试即可。

相关：[[project-env-snapshot-2026-11-09]]
