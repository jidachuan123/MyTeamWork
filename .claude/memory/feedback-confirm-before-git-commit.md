---
name: feedback-confirm-before-git-commit
description: 每次执行 git commit / git push 前，必须先列清单让用户确认，不得直接提交
metadata:
  type: feedback
---

2026-11-09 用户要求：「每次提交的时候提醒我，让我确认」。用户选择了「只写记忆规则」（不配 PreToolUse 钩子）。

**Why:** 仓库里有明文密码（`provider-service/config/application.yml`、`application-rds27.yml`）、会推到 GitHub，且用户自己常在不同分支间切换（`test-AI` / `main` / `11`）。提交前让用户看一眼文件清单和分支，是最后一道人工闸门。

**How to apply:**

1. 执行 `git commit` / `git push` / `git add` 前，**先**输出：
   - 当前分支 + 是否有上游
   - `git status --short` 的文件清单（区分「已暂存 / 未暂存 / 未跟踪」）
   - 本次 commit message 草稿
2. 等用户明确确认后再执行。用户已经在本轮说了「提交吧」但没看过清单时，仍要展示清单——「确认」指看过内容，不是看过意图。
3. 发现待提交内容含 **明文密钥/密码/授权码**、**误入的构建产物**（`target/`、`dist/`、`logs/`）、或**不该提交的配置**时，主动指出来再问。
4. 用户选择 push 时，明确说明会推到哪个远程分支（尤其当前分支无上游时会新建）。

**注意：这是行为规则，不是强制机制。** 用户明确不要 hook，所以它依赖 CLAUDE.md 被加载 + 我自觉遵守。若以后觉得不可靠，可重新评估加 `PreToolUse` 钩子。

相关：[[reference-known-issues-doc]]、[[feedback-remind-to-save-memory]]
