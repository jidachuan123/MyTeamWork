---
name: project-rds27-datasource
description: 2026-11-09 新增 .27 备机数据源（Rds27DataSourceConfig + application-rds27.yml），@Lazy 未启用，尚无调用方
metadata:
  type: project
---

2026-11-09 新增两套指向 **192.168.0.27:1543**（新备机/测试源）的数据源，与现有四套并存、完全隔离：

- `Rds27DataSourceConfig`（`provider-service/src/main/java/com/demo/provider/config/`）
  → `rds27BcDataSource` / `rds27BcJdbcTemplate`（RDS_BC，1141 张表）
  → `rds27ScDataSource` / `rds27ScJdbcTemplate`（RDS_SC，1399 张表）
- `application-rds27.yml`（`provider-service/src/main/resources/`），账号 `mis_user / Enjoyit.2SqlServer`，来源是「业务系统相关地址.xlsx」第 1 行。

**两个刻意的设计**（改之前先读懂注释）：

1. **所有 Bean 都 `@Lazy`** —— 当前只用于排查/对比，没有代码注入，不建连也不影响启动。
2. **Hikari `initializationFailTimeout = -1`** —— .27 宕机也不会让 provider-service 启动失败，首次 `getConnection` 才报错。这与 .66 那两套「必须连通才能启动」的行为**故意不同**。

**加载方式**：`@PropertySource("classpath:application-rds27.yml")` + `YamlPropertySourceFactory`，和 sd3/sd4 一样 —— **只能重打包生效，放外部 `config/` 目录无效**；可用命令行 `--rds27.bc.url=...` 临时覆盖。

**当前状态（2026-11-09）**：两个文件都是 **git 未跟踪的新增文件**，全仓库 grep 不到任何 `@Qualifier("rds27...")` 注入点。真正启用要自己写 Service 注入 + 确认 .27 上库结构是否与 .66 一致。

相关：[[project-env-snapshot-2026-11-09]]
