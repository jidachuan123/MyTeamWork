# MEMORY.md

MyTeamWork（`C:\work\MyTeamWork`）项目记忆索引。每条一行：`- [标题](文件.md) — 一句话钩子`。

> 本目录放 `CLAUDE.md` / `docs/` / git 历史里没有的事实，以及**从 CLAUDE.md 下沉的细节**（口径、坑、SSO、预计算）。
> `CLAUDE.md` 现在是"必读骨架"，需要展开时按本索引跳转。

## 用户与协作方式
- [用户画像与语言约定](user-role-and-language.md) — 全栈负责人；全程中文，注释/日志也中文
- [服务由用户自己启动](feedback-user-starts-services.md) — 不要代启常驻服务，只起临时端口短命实例
- [curl 必须 --noproxy](feedback-curl-noproxy.md) — 本机有 127.0.0.1:7688 代理，否则 502 假象
- [记忆库位置](feedback-memory-location.md) — 就是这里，不要再写 `~/.claude/projects/...`
- [学到新知识先提醒再存](feedback-remind-to-save-memory.md) — 新增/更新/删除记忆都要先提醒，不静默写入也不闷着不改
- [提交前必须让用户确认](feedback-confirm-before-git-commit.md) — git commit/push 前先列分支+文件清单+message 草稿

## 报表口径与技术细节（自 CLAUDE.md 下沉）
- [SD1~SD4 完整口径](reference-sd-calibers.md) — 同比/增长率/分组/库存排除/渠道补差/画布/配置驱动截图
- [已知坑与手改速查](reference-pitfalls.md) — 企微 DNS/截图轮询/多语句 SQL/中文 key/编码 + 改配置三铁律
- [provider 内部机制](reference-provider-internals.md) — 预计算 dw、统一响应体、定时任务状态、外部 config 生效差异快照
- [consumer 认证与 SSO](reference-consumer-auth.md) — JWT 拦截链、权限、Feign、门户票据

## 项目状态 / 进行中的事
- [.27 备机数据源（2026-11-09 新增）](project-rds27-datasource.md) — Rds27DataSourceConfig + application-rds27.yml，@Lazy 未启用无调用方
- [环境快照 2026-11-09](project-env-snapshot-2026-11-09.md) — 工具链路径、桌面 bat、consumer 8002/8006 错配、deploy 空骨架

## 仓库外资料
- [.workbuddy 历史记忆库](reference-workbuddy-memory.md) — `C:\work\.workbuddy\memory\`，SD1~SD4 口径/踩坑的唯一记录处
- [已知问题清单](reference-known-issues-doc.md) — `C:\work\docs\已知问题清单.md`，A1~A9 数据风险，带核实标记
