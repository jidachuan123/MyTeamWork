---
name: feedback-memory-location
description: 记忆库位置 = 项目根 .claude/memory/，不要再用 ~/.claude/projects/.../memory/
metadata:
  type: feedback
---

2026-11-09 用户要求：记忆文件放到项目根目录，最终选定 `C:\work\MyTeamWork\.claude\memory\`。此前我已按 harness 默认写到 `C:\Users\59111\.claude\projects\c--work-MyTeamWork\memory\`，用户明确改到项目内。

**Why:** 记忆要跟着仓库走，团队/其他机器 checkout 后也能读到；`C:\work\.workbuddy\memory\` 那种"工作空间级"位置则在所有项目间共享，容易串味。

**How to apply:** 后续读写记忆一律用 `C:\work\MyTeamWork\.claude\memory\`，索引文件是该目录下的 `MEMORY.md`。`~/.claude/projects/...` 那份已清空，不要再往里写。

相关：[[reference-workbuddy-memory]]
