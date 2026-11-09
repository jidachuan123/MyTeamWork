---
name: reference-provider-internals
description: 预计算 dw、统一响应体、定时任务状态、外部 config 生效差异快照，从 CLAUDE.md 拆出
metadata:
  type: reference
---

## 预计算（dw schema）

宽表 `dw.rpt_sale_detail_precompute`（RDS_SC）缓存 SD1/SD2 的引擎结果：报表1 逐机构跑 3 档 × MOM/YOY = 6 次 `dm.up_GetFine_Run`；报表2 多机构整串一次调用，落 `dept_levels=0`。

- 查询 `org_code` 用 **`LIKE ? + '%'` 前缀匹配**（前端传短码 `1101` 命中 1101xxx，`1191001` 退化为精确）。类注释写「IN 子句」已过时。
- 幂等删除两套：报表1 带 `org_code`；报表2 按 `dept_levels=0` 整批删。
- NULL 写入必须按 `PrecomputeCols.SQL_TYPES` 指定 jdbcType，否则 mssql-jdbc 发 `varbinary` 报错 257。
- 列常量 `PrecomputeCols` 由 `sql/precompute/gen_java_cols_class.py` 从建表 SQL 生成，**不要手改**；加列改 DDL 后重跑脚本。
- 🔴 安全红线：只读写 `dw.` 自有对象，绝不 DDL/UPDATE/DELETE 生产表。

## 定时任务当前状态

- `SalesDailyReportScheduler` / `SalesDetail3Scheduler` / `SalesDetail4Scheduler`：`@Scheduled` 生效。
- `SaleDetailPrecomputeScheduler`：**`@Scheduled` 已被注释掉**，预计算只能手动 POST `/trigger`。→ 数据中台宽表可能长期是旧数据且无提示（`docs/已知问题清单.md` A2）。

## 统一响应体

```java
ApiResult<T> { long code; T result; String msg; page; size; count; total; traceId; }
```

- **`code` 0 = 成功**（`ErrorCode.SUCCESS(0)`），1=请求失败、2=参数错误、3=不存在、500=系统异常。**不是 200**。
- `page/size/count/total` 四个字段存在但**没有任何 setter 调用方，实际永远是 0**。
- ⚠️ **大部分报表接口根本不用 ApiResult**，直接返回 `List`/`Map`：`/provider/sales/detail`、`/sales/precompute`、`/sd3/query`、`/sd4/query`。只有手动触发类接口包 ApiResult。前端也据此直接取数组。
- consumer 的 `GlobalExceptionHandler` 只处理 401/403，**没有 `Exception.class` 兜底** → provider 异常经 Feign 过来会走 Spring 默认 500，结构不是 ApiResult。
- `/provider/**` 在后端**没有任何鉴权**，只有 `/consumer/user|report|external/**` 经过 Shiro。

## 外部 config 生效差异（快照，2026-11-09）

`provider-service/config/application.yml` 相对 jar 内 `src/main/resources/application.yml` 的差异：

| 键 | jar 内 | 外部 config（生效） |
|---|---|---|
| `main.mail-to` | `""` | `591111450@qq.com` |
| `main.use-fixed-date` | false | **true** |
| `main.start/end-date` | 2026-10-01/10-31 | 2026-09-01/09-30 |
| `main.cmp-start/end` | 2026-09-01/09-30 | 2026-08-01/08-30 |
| `main.yoy-start/end` | 2025-10-01/10-31 | 2025-09-01/09-30 |
| `main.wecom-webhook` | 有值 | **`""`（主组当前不推企微）** |
| `secondary.wecom-webhook` | 有值 | **`""`（副组当前不推企微）** |

后果：主组 SD2 截图当前**不含「当日库存金额」列**（`use-fixed-date=true` → `hideStock`），且主组查询的是 2026-09 的固定日期。

⚠️ 这是快照，改配置后要重新核对，勿当成永久事实。

相关：[[reference-sd-calibers]]、[[reference-pitfalls]]
