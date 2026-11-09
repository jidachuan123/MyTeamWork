# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **本文件只放"必读骨架"**：模块、端口、构建、数据源、报表与定时任务的对应关系、红线。
> 口径细节、踩坑清单、排障步骤、SSO 细节、配置快照等**按需展开的内容已下沉到 `.claude/memory/`**，见下方「资料地图」。

## 项目概览

Spring Cloud（Spring Boot 2.7.18 / Spring Cloud 2021.0.8 / Java 16）后端工程，共 3 个模块。前端不在本仓库。

| 模块 | 端口 | 作用 |
|---|---|---|
| `eureka-server` | 8761 | 服务注册中心（必须先启动） |
| `provider-service` | 8001 | 业务主体：报表取数、定时截图、邮件/企微推送、权限数据、预计算、外部系统对接 |
| `consumer-service` | 8002 | 门户/网关层：Shiro + JWT 认证鉴权、Feign 转发到 provider、门户 SSO |

### 关联工程（同一工作空间 `C:\work`，改接口前务必知道）

| 工程 | 端口 | 说明 |
|---|---|---|
| `MyVue` | **3001** | 运营后台主入口（`npm run dev`） |
| `MyVue`（第二入口） | **3004** | 经营驾驶舱大屏（`npm run dev:cockpit`，入口 `cockpit.html`，产物 `dist-cockpit/`） |
| `MyPortalVue/vue-portal` | 3002 | 门户落地页（Element Plus + Pinia，公开无登录） |
| `MyDataVue` | 3003 | 数据中台（查预计算宽表） |
| `HerTeamWork` | 8003 | SSO 接收方子系统 `her-subsystem`，独立 Maven 工程，只用共享 JWT 密钥 |

前端请求层是**原生 `fetch`**（`MyVue/src/utils/request.js`），不是 axios；`BASE_URL='/api'`，由 vite 代理去掉 `/api` 前缀后转发：`/api/consumer` → consumer，`/api/provider` → provider。

⚠️ **consumer 端口在本机存在 8002/8006 两种状态**，且 `MyVue`/`cockpit` 代理指 8002、`MyPortalVue`/`MyDataVue` 指 8006，实际连通性取决于用哪个脚本启动。动手前先 `netstat -ano | findstr :800` 确认，并同步所有引用处（细节见 `.claude/memory/project-env-snapshot-2026-11-09.md`）。

## 资料地图

**开新会话先读 `.claude/memory/MEMORY.md`**（索引）。按任务跳转：

| 要做什么 | 先读 |
|---|---|
| 改 SD1~SD4 取数/聚合/截图 | `.claude/memory/reference-sd-calibers.md` — 四张报表完整口径 |
| 判断「截图为什么和页面对不上」 | 同上 + 铁律：**截图 = 页面**，同源处必须同步 |
| 改推送 / 截图 / SQL / 配置踩坑 | `.claude/memory/reference-pitfalls.md` — 已知坑 + 手改速查表 + 改配置三铁律 |
| 动预计算 dw / 统一响应体 / 看 config 生效差异 | `.claude/memory/reference-provider-internals.md` |
| 动 consumer 认证、权限、门户 SSO | `.claude/memory/reference-consumer-auth.md` |
| 接手不熟的任务、查端口/工具链实况 | `project-env-snapshot-2026-11-09.md`、`project-rds27-datasource.md` |
| 找历史决策/踩坑 | `C:\work\.workbuddy\memory\`（仓库外，做 SD1~SD4 前读 `项目上下文-报表推送与部署细节.md`） |
| 找已知缺陷 | `C:\work\docs\已知问题清单.md`（A1~A9，带 `[已核实]`/`[待核实]`）；全景架构见 `C:\work\docs\系统架构与技术栈.md` |

**记忆库规则**：`.claude/memory/MEMORY.md` 是索引，正文单条存放。只记 `CLAUDE.md`/`docs`/git 里没有的事实（用户偏好、环境实况、进行中的决策），不重复口径细节。

学到新知识时**先提醒用户确认再写**；读到记忆与当前代码/配置对不上时，**当场指出并提议更新或删除**，不要沿用旧值也不要不吭声地改掉（细则见 `.claude/memory/feedback-remind-to-save-memory.md`）。

**git 提交规则**：执行 `git commit` / `git push` 前必须先给出「当前分支 + `git status --short` 文件清单 + commit message 草稿」并等用户确认；发现明文密钥、误入的构建产物要主动指出。细则见 `.claude/memory/feedback-confirm-before-git-commit.md`。

## 构建与运行

`mvn` 不在 PATH 上。本机两份安装，均可用：

```bash
"C:/Program Files/apache-maven-3.9.16-bin/bin/mvn" -DskipTests package   # 3.9.16
"C:/work/apache-maven-3.9.6/bin/mvn.cmd" -DskipTests package            # 3.9.6（旧）
```

启动顺序：eureka-server → provider-service → consumer-service。**没有测试代码**（任何模块都没有 `src/test`），也没有 mvnw。验证方式是启动服务打接口。

### provider-service 的工作目录很重要

provider 会读取**外部配置** `provider-service/config/application.yml`（Spring Boot 默认 `./config/` 搜索位置），优先级高于 jar 内配置，因此工作目录必须是 `provider-service/`：

- IDEA 直接跑 `ProviderApplication`（工作目录默认模块目录）✅
- 命令行在 `provider-service/` 下执行 `java -jar target/provider-service-1.0.0.jar`

改配置的三条铁律（详细版见 `reference-pitfalls.md`）：
1. 改运行中的 jar 要改**外部** `config/application.yml`（改 src 无效，除非重打包）；
2. 外部 config 里的 **List 整体替换、不与 jar 合并**（`screenshots` 必须写全）；
3. `application-sd3.yml` / `application-sd4.yml` / `application-rds27.yml` 走 `@PropertySource("classpath:...")`，**只能重打包生效**；可用命令行 `--sd3.cron=...` 覆盖。

当前外部 config 的实际生效值（固定日期 2026-09、企微关、`hideStock` 生效）见 `reference-provider-internals.md` 的「外部 config 生效差异」表——那是快照，改完要重新核对。

## provider-service 架构

### 数据源并存（新增数据源前必看）

| Bean | 库 | 配置来源 | 使用者 |
|---|---|---|---|
| `primaryDataSource`（@Primary） | SQL Server `RDS_BC` @.66 | `application.yml` `spring.datasource` | 商品/用户 MyBatis（`com.demo.provider.mapper`）、`jdbcTemplate` |
| `scDataSource` | SQL Server `RDS_SC` @.66 | `application.yml` `spring.datasource-sc` | **SD1/SD2 取数、盘点报表、预计算 `dw` schema** |
| `sd3DataSource` / `sd3JdbcTemplate` | MySQL `bcm` | `application-sd3.yml` | SD3 |
| `sd4DataSource` / `sd4JdbcTemplate` | SQL Server `RDS_SC` @.66 | `application-sd4.yml` | SD4 |
| `rds27Bc*/rds27Sc*` | SQL Server @**.27 备机** | `application-rds27.yml` | 暂无调用方（全 `@Lazy`） |

- 🔴 **预计算宽表 `dw.rpt_sale_detail_precompute` 在 RDS_SC**（`scDataSource`），不是 RDS_BC — 架构文档第 439 行写错了。
- SD3/SD4/RDS27 配置独立文件 + `YamlPropertySourceFactory`，刻意与主配置隔离。
- MyBatis 按包拆两个 `SqlSessionFactory`：`com.demo.provider.mapper` → `bcSqlSessionFactory`，`com.demo.provider.auth.mapper` → `scSqlSessionFactory`。新 mapper 包必须挂到其一。
- **`PrimaryJdbcTemplateConfig` 显式定义 `@Primary` 的 `jdbcTemplate`**：一旦存在其他 `JdbcTemplate` Bean，Boot 自动配置失效，按类型注入会静默连错库或启动报 `NoUniqueBeanDefinitionException`。新增 JdbcTemplate 必须 `@Qualifier`。

### 报表体系（核心业务）

四张报表各自独立：**取数服务 + HTML/截图服务 + 调度器 + 配置节点**，互不引用（只复用截图与推送方法）。

| 报表 | 取数 | 数据源 | 定时器 | cron | 推送配置 |
|---|---|---|---|---|---|
| SD1 销售详情1 | `service/SalesDetailService`（JDBC 批处理） | RDS_SC | `SalesDailyReportScheduler` | `report.daily.cron` = `0 20 08` | `report.daily.main/secondary` |
| SD2 销售详情2 | `report/SalesDetail2ReportService`（调 SD1 服务两遍） | RDS_SC | 同上（同一次 `runNow` 内） | 同上 | 同上 |
| SD3 线上销售 | `report/SalesDetail3Service` | MySQL bcm | `SalesDetail3Scheduler` | `sd3.cron` = `0 22 08` | `application-sd3.yml` |
| SD4 配送调拨 | `report/SalesDetail4Service` | RDS_SC | `SalesDetail4Scheduler` | `sd4.cron` = `0 24 08` | `application-sd4.yml` |

四个 cron 错开是为了避免并发抢 headless 浏览器，**改 cron 必须保持错峰**（改完同步改 yml 里的中文注释）。

**流水线**：取数 → 拼 HTML（写 `output-dir`）→ 本机 Chrome/Edge `--headless` 截图 → 邮件附件 + 企微图片。

**定时任务状态**：三个报表调度器 `@Scheduled` 生效；`SaleDetailPrecomputeScheduler` 的 **`@Scheduled` 已被注释掉**，预计算只能手动 POST `/trigger`。

### 🔴 最高红线：「截图 = 页面」

同一入参下截图与页面必须行/列/值完全一致。改任一处聚合逻辑必须同步其余同源处：

| 报表 | 同源处 |
|---|---|
| SD1 | ① `MyVue/src/views/SalesDetail.vue` ② `MyDataVue/src/views/SalesDetail3.vue`（文件名与语义不符，是预计算版 SD1）③ `SalesDailyReportService.java` ④ `MyVue/public/sales-report1.html`（对照工具） |
| SD2 | ① `MyVue/src/views/SalesDetail2.vue` ② `SalesDetail2ReportService.java` ③ `MyVue/public/sales-report2.html`。**`MyDataVue/src/views/SalesDetail2Data.vue` 已被用户明确排除，不要再改、也不要提醒它漏改** |

其它长期红线：对照页**不隐藏库存金额 = 0 项**；预计算只读写 `dw.` 自有对象，绝不 DDL/UPDATE/DELETE 生产表；`RDS_BC`/`RDS_SC` 的 ERP 源表全程只读。

## consumer-service 架构（骨架）

- **无状态 JWT**：Shiro 关 session，`JwtFilter` 校验 `Authorization: Bearer <token>`，`JwtRealm` 每请求回查 provider 两次 Feign（无缓存）。
- 拦截链：login/register anon；`/consumer/user|report|external/**` jwt；**`/**` 兜底 anon**。新增受保护路径必须显式加链，否则默认放行。
- `/provider/**` 后端**没有任何鉴权**。
- 完整细节（拦截链、权限、Feign、门户 SSO 票据）见 `.claude/memory/reference-consumer-auth.md`。

## 手动触发接口（调试用）

```bash
# SD1/SD2：只发这一张；body 是页面 queryForm（orgCode/deptLevels/6 个日期）
POST http://localhost:8001/provider/sales/report/detail1/trigger
POST http://localhost:8001/provider/sales/report/detail2/trigger
GET  http://localhost:8001/provider/sales/report/test?date=2026-08-14   # 仅截图不发送
GET  http://localhost:8001/provider/sales/trigger-daily-report          # 完整日报（主+副，异步）
GET  http://localhost:8001/provider/sd3/trigger                         # SD3 手动推送
GET  http://localhost:8001/provider/sd4/trigger                         # SD4 手动推送
# 预计算：查表 / 跑批回补 / 跑批日志
GET  http://localhost:8001/provider/sales/precompute?queryDate=...&comparisonType=MOM&orgCode=1101001&reportType=SALE_DETAIL_1
POST http://localhost:8001/provider/sales/precompute/trigger?queryDate=...&orgCode=...&reportType=...
GET  http://localhost:8001/provider/sales/precompute/logs?limit=20&reportType=SALE_DETAIL_1
```

本机有 HTTP 代理，curl 这些地址必须加 `--noproxy '*'`。

## 数据库脚本

`sql/` 下脚本都需**人工在 SSMS 执行**，服务不会自动建表：

- `sql/shiro_auth_ddl.sql` — `sys_user/sys_role/sys_permission` 等认证表（RDS_SC）
- `sql/portal_sso_ddl.sql` — SSO 可选表（当前无状态 JWT 票据不需要）
- `sql/precompute/01_create_dw_schema.sql` + `02_column_remarks.sql` — 预计算 `dw` schema 与 208 列宽表

数据库：RDS_BC / RDS_SC `192.168.0.66:1543`，bcm MySQL `192.168.0.62:3416`，备机 `192.168.0.27:1543`。**RDS_BC/RDS_SC 的 ERP 源表全程只读**，唯一例外是往 RDS_SC 的 `dw.*` 自有对象写入。账号密码目前明文在 yml。
