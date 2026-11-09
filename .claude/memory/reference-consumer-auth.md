---
name: reference-consumer-auth
description: consumer-service 的 JWT/Shiro 拦截链、权限、Feign、门户 SSO 细节，从 CLAUDE.md 拆出
metadata:
  type: reference
---

## consumer-service 架构

- **无状态 JWT**：Shiro 关 session，`JwtFilter` 校验 `Authorization: Bearer <token>`，`JwtRealm` 再回查 provider（**每个请求两次 Feign，无缓存**）。
- 拦截链：`/consumer/auth/login|register` anon；`/consumer/user|report|external/**` jwt；**`/**` 兜底 anon**。新增受保护路径必须显式加链，否则默认放行。`/consumer/portal/**` 未列链 → anon（Controller 自己解析 token）。
- 权限用 `@RequiresPermissions(...)`，编码来自 provider 的 `sys_permission.perm_code`。
- 密码 Shiro `MD5(password+salt, 2 次)`；provider 启动时 `AuthController.initData()` 在表为空时建 `admin`。
- Feign 每个客户端必须不同 `contextId`。转发层**零加工**（透传参数、原样返回，不解包不判 code）。
- `PortalController` SSO：`/sso-ticket` 用同一 `jwt.secret` 签发 2 分钟、带 `ticketType=SSO` + `targetApp` 的票据；子系统共享密钥本地验签（方式 A），`/validate-ticket` 只给无密钥第三方（方式 B）。`jwt.secret` 变更必须同步所有子系统。
- 子系统登记在 `portal.subsystems`（`url` → 外链跳转，`path` → 前端模拟路由，`with-ticket: false` → 纯跳转不带票）。

相关：[[reference-provider-internals]]
