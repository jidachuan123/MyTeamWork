---
name: reference-pitfalls
description: 已知坑完整清单（企微 DNS/截图/多语句 SQL/中文 key/编码等）与手改行为速查表，从 CLAUDE.md 拆出
metadata:
  type: reference
---

## 已知坑（改动前必看）

- **企微重试间隔必须 12 秒**：JDK 对 DNS 失败有 **10 秒负缓存**，0.8s/1.6s 的退避全部命中缓存、一次查询都没发出去（2026-11-05/06 真实事故）。`waitForDns(url, 90)` 在推送前前置探测。
- **企微摘要必须 text 消息**：markdown 被转发到微信客户端会显示「暂不支持此消息类型」。`WeComBotService` 会自动清洗 markdown 语法，text 上限 2048 字节按 UTF-8 边界截断。
- **企微图片 ≤ 2MB、间隔 400ms**（限 20 条/分钟）。群机器人原生支持 image；钉钉自定义机器人**不支持**图片。
- **`发送异常：qyapi.weixin.qq.com`（只有域名）** 通常是 `UnknownHostException` = 本机 DNS 不可用，不是企微封禁。排查顺序：`nslookup` 逐个网卡 DNS → `curl --noproxy '*'` → 才怀疑代码。
- **截图前先删除同名 PNG**：否则重发时轮询到旧图会误判成功。判成功靠轮询文件生成（最长 90 秒），不依赖 Chrome 进程退出码（Windows 下启动器会立即退出）。每个进程用独立临时 `--user-data-dir`，避免与桌面 Chrome 主实例冲突。
- **`output-dir` 必须是绝对路径**：IDEA 工作目录不是 `provider-service`，相对路径会 `NoSuchFileException`。
- **多语句 SQL 必须整段同一 Connection 执行**：SD1/SD2/SD4 含 `DECLARE` + 临时表 + 存储过程，`Statement.execute` 后取**最后一个** ResultSet。
- **SD1 SQL 参数是字符串拼接**（只转义 `'` → `''`），没有参数化。
- **Spring Boot Map 中文 key 必须写 `"[美团闪购]"`**（方括号+引号），否则 key 被吞掉、补差静默失效。
- **JDBC MySQL `characterEncoding` 只能写 `utf8`**，`utf8mb4` 会启动失败。
- **SD3 SQL 的 `COLLATE utf8mb4_general_ci` 不能删**：bcm 库列是 `utf8mb4_general_ci`，会话变量是 `utf8mb4_0900_ai_ci`，不统一会报 Illegal mix of collations。
- **文档/注释里的时间经常过时**：`application.yml` 第 66 行曾把 cron `0 35 08` 注释成「每天上午 8:29」，导致 8:33 检查时误判失败。改 cron 时同步改注释。
- 仓库里注释、日志、异常文案全为中文，新代码保持同样风格（带日期的中文变更说明是既有习惯）。

## 手改行为速查

| 需求 | 位置 |
|---|---|
| SD2 分组 / 合计特例 / 隐藏门店 | `SalesDetail2ReportService` 的 `ORG_GROUP_OVERRIDE` / `getGroup` / `buildSubtotal`（1102911）/ `buildHtml`（`stockAmount <= 0`） |
| SD3 渠道补差 | 淘宝闪购在 `SalesDetail3Service` 常量；其他渠道在 `application-sd3.yml` |
| SD3 订单数口径 | `SalesDetail3Service` 明细循环里的 `含税销售金额 > 0` |
| SD1 库存排除清单 | `SalesDetailService` 最终 SELECT 的 `mykucun` 子查询（另需同步 SD4 SQL 尾部同名子查询） |
| 截图画布尺寸 | SD3 `SCREENSHOT_WIDTH` + colgroup；SD4 宽度/行高三常量 + colgroup |
| 预计算档位/删除条件 | `PrecomputeRepository` 的 `DELETE_SQL_R1/R2` + `queryByReportType` 前缀 LIKE |
| 前端加页面 | `MyVue/src/views/Xxx.vue` + `src/router/index.js` + `src/App.vue` 导航（三处） |

## 配置修改三铁律

1. 改运行中的 jar 要改**外部** `config/application.yml`（改 src 无效，除非重打包）；
2. 外部 config 里的 **List 整体替换、不与 jar 合并**（`screenshots` 必须写全）；
3. `application-sd3.yml` / `application-sd4.yml` / `application-rds27.yml` 走 `@PropertySource("classpath:...")`，**只能重打包生效**，放到外部 config 目录无效（但可用命令行 `--sd3.cron=...` 覆盖）。

相关：[[reference-sd-calibers]]、[[reference-provider-internals]]
