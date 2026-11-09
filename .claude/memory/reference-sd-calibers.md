---
name: reference-sd-calibers
description: SD1~SD4 的完整口径细节（同比口径/增长率/分组/补差/画布/配置驱动截图），从 CLAUDE.md 拆出
metadata:
  type: reference
---

从 `CLAUDE.md` 拆出的报表口径详情。**红线：同一入参下截图与页面必须行/列/值完全一致**，改任一处聚合逻辑必须同步同源处。

## SD1 / SD2

- **SD1 取数 6 次**：{环比, 同比} × {deptLevels=3 明细, 2 部门合计, 空 机构汇总}。返回**裸 `List<Map>`**（无 ApiResult 包装）。
- **SD1 同比是「去年同一天」，SD2 同比是「去年就近同周几」**（`calcYoySameDow`）——两报表同比口径不同，别互相套用。
- **SD2 增长率分母是带符号对期值；SD1 用 `|对期|`**。两种增长率都是**百分数**（后端已 ×100）。
- **SD2 分组**：按机构编码**前四位**（1101/1102/1103/1104…）升序，组内按编码字符串升序，每组末尾插一行「合计」（文案只叫「合计」，无组名前缀）。特例 `ORG_GROUP_OVERRIDE = {"1106" → "1104"}`。
- **`1102911`（巨野便利店配送中心）整行不进门店合计**，但该行本身照常显示。
- **`1101951/1103951/1501001/1501031/1101099` + `1108%` + `13%` 在库存子查询 `mykucun` 里被硬编码排除**（`SalesDetailService` 最终 SELECT 附近）。`getStockOnlyRows`（union-stock 补行）**没有**这套排除规则。
- **库存 = 0 的门店是「隐藏但参与计算」**（`v-show` / 渲染层 `continue`），不是过滤；SD2 隐藏阈值是 `stockAmount <= 0`。
- **`report.daily.exclude-org-codes` 是全局配置**：SD1 在取数层过滤，SD2 再过滤一次（双重），预计算落库时也被过滤。改这个配置**不会自动重跑历史预计算数据**。
- SD1 部门分组按**部门编码3 前 2 位**：11 生鲜一部 / 12 生鲜二部 / 13 食品部 / 15 非食部，未知前缀整行丢弃。

## SD3

- 取数必须在**同一 Connection** 上先 `SET @开始日期/@结束日期/@机构/@渠道/@订单号` 再执行 SQL（连接池换连接变量会丢）。
- 机构参数是**逗号分隔精确匹配**（`FIND_IN_SET`），Java 侧 `normalizeOrgCodes` 先归一化去重。
- **订单数只统计「含税销售金额 > 0」的订单号去重**（`LinkedHashSet`）；售后负数行不计单，但金额仍累加负数。
  - ⚠️ SQL 文件头注释声称有 `AND 含税销售金额 > 0` 过滤，**实际 SQL 里没有这条**，过滤只在 Java 侧生效。
- **渠道补差**：最终销售额 = 分组求和 + 补差。`淘宝闪购` = 该组包装费求和（Java 常量，不是配置）；其他渠道 = 订单数 × `sd3.chnl-per-order-fee[渠道]`。补差在分组层叠加，合计/总计/客单价都用补差后金额。
- 7 列 + 门店编码/名称纵向合并（`storeRowSpan`）；多渠道才加门店「合计」行；总计行左侧 `colspan=4`。
- 画布 680px（竖版），7 列 colgroup 40/82/194/92/106/58/84 = 656。**改宽度必须同步常量与 colgroup**。
- 截图头部**不显示机构**，只显示日期区间 + 渠道。

## SD4

- 页面只暴露 7 个参数（6 个日期 + 发货机构），其余过滤（收货机构/部门/仓库/品类/类型/记账类型）全在 `application-sd4.yml` 白名单里。
- **SQL 里绝不写 ORDER BY**：会触发全表排序，实测慢约 4 倍。排序在 Java 内存中做，页面与截图天然一致。
- 两套画布：9 列 1350 / 不含库存 1202，行高 56；列宽写死 colgroup，**改宽度要同时改常量**。
- 增长率是**小数**（前端 ×100），null 显示「-」。

## 截图方法（SD1/SD2/SD3/SD4 共用）

`SalesDailyReportService.screenshot(...)` 同包可见，三个重载：
1. 1900×30 —— SD1/SD2
2. 只给宽度 —— <1200 自动用行高 76，SD3 走这条
3. 宽度 + 行高 —— SD4

邮件 `SalesDailyReportService.sendMail(...)`；企微 `WeComBotService.sendReport(...)`。两通道互不冲突：`mail-to` 和 `wecom-webhook` 都填就都发。

## 配置驱动的截图任务（SD1/SD2）

`screenshots` 列表声明任务（`type`=sd1/sd2、`org-code`、`tag`、可选的 `union-stock-codes`），增删截图**不改代码**。同报表多张不同机构时 `tag` 必填（否则文件名互相覆盖）。

日期模式用**显式开关**而非「有没有填日期」：

- `use-fixed-date: false`：本期=前一天、环比=前两天、同比=去年同日（SD2 为就近同周几）；
- `use-fixed-date: true`：用配置的 6 个日期；某项留空回退推算值并打 WARN。

**`use-fixed-date=true` 时 SD2/SD4 截图隐藏「当日库存金额」列**（库存是实时快照，补历史时误导）。SD2 连带：① 停用「库存≤0 不显示行」② 不再追加 union-stock 补行（否则多出全 0 空行）。手动触发与定时任务共用 `ReportDailyProperties.resolveUseFixedDate(type)` 保证口径一致。

相关：[[reference-provider-internals]]、[[reference-pitfalls]]
