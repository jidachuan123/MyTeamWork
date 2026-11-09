---
name: reference-known-issues-doc
description: C:\work\docs\已知问题清单.md（2026-11-09）— 9 项数据风险 + 重复实现清单，带 已核实/待核实 标记
metadata:
  type: reference
---

`C:\work\docs\已知问题清单.md`（9562 字节，2026-11-09 建）是代码走查 + 与架构文档/`.workbuddy` 交叉核对的结果，分 A（正确性/数据风险，A1~A9）与 B（一致性/重复实现，至少 B1 三四处手工同步、B2 增长率三套约定）。

**Why 值得记：** 里面把「口径不一致」的根源系统化列出并标了 `[已核实]` / `[待核实]`，比 `CLAUDE.md` 的零散坑更成体系；做重构或排查"数对不上"时先看这里能省一轮排查。

**最该先看的几条**（都已核实）：
- A2 预计算 `@Scheduled` 被注释 → 数据中台宽表长期可能是旧数据且无提示
- A3 `output-dir` 指向 `target/report` → `mvn clean` 会删当天截图/HTML；四报表共用目录同日期会互相覆盖
- A4 推送无投递记录表、无幂等 → 「今天到底发没发」只能翻日志（2026-11-05/06 争论的根源）
- A5 数据库密码/邮箱授权码/jwt.secret/企微 webhook 明文且被 git 跟踪

**How to apply:** 接手任务先花两分钟扫 A 段；`[待核实]` 的条目动手前用代码复核行号，别直接引用。

相关：[[reference-workbuddy-memory]]
