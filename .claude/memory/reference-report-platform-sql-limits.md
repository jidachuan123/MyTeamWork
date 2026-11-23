---
name: reference-report-platform-sql-limits
description: 百购 ERP 报表平台会解析改写 SQL —— Navicat 能跑但平台报 ")" / AND 语法错的根因与规避写法
metadata:
  type: reference
---

## 核心事实

**报表平台不是把 SQL 原样丢给 SQL Server，而是会解析并改写它。**
所以 **Navicat 能执行 ≠ 平台能执行**。SQL Server 认可语法，平台解析器仍可能拆坏。

典型报错（2026-11-17 实测）：

```
")"附近有语法错误。  关键字 'AND' 附近有语法错误。
")"附近有语法错误。  ")"附近有语法错误。
```

括号明明配平、Navicat 跑得好好的 —— 那这就是平台拆坏的，**不要去补括号，补不出来**。

## 真正的触发条件

不是「嵌套几层」的问题（sqlA 有 6 层照样能跑），而是**最外层 SELECT 列表里放了什么**：

| 最外层 SELECT 内容 | 平台表现 |
|---|---|
| `t.[字段名]`（字段引用） | ✅ 安全 |
| `SUM(a - b)` | ✅ 安全 |
| `ROUND(SUM(x) / SUM(y), 2)` — 简单聚合除法，≤3 层 | ✅ 安全（sqlB 原版就是这样） |
| `ROUND(MAX(a) / MAX(b), 4)` 埋在 5 层函数 + IIF 里 | ❌ **本次踩的坑** |
| `SUM(SUM(x)) OVER (PARTITION BY ...)` | ❌ 高风险 |
| `BETWEEN ... AND ...` 写在 JOIN 的 ON 里 | ❌ 可能被按 `AND` 拆坏 |

**规律：平台会深度解析最外层 SELECT。里面越"干净"越安全。**

sqlA 之所以没事，是因为它**分两层**：内层算比率，外层只做 `CONVERT(ROUND(t.[月度任务完成率]*100,1))` —— 平台解析外层时看到的是字段名，不是表达式。

## 解决公式

> **所有计算提前在临时表做完，最外层 SELECT 只留 `MAX(临时表.列名)` + 简单格式化。**

改造前（报错）：
```sql
IIF(@查询维度='月',
    IIF(MAX(tm.c_task_sum) IS NULL OR MAX(tm.c_task_sum) = 0, NULL,
        CONVERT(VARCHAR(20), CAST(ROUND(
            ROUND(MAX(ss.c_sale_total) / MAX(tm.c_task_sum), 4) * 100, 1) AS DECIMAL(10,1))) + '%'),
    NULL) AS 月度任务完成率
```

改造后（通过）：
```sql
-- ① 临时表提前算好比率
CREATE TABLE #ttb_calc (... c_sale_total DECIMAL(18,2), c_rate_m DECIMAL(18,6) ...)
INSERT INTO #ttb_calc (...)
SELECT ..., CASE WHEN ISNULL(m.c_task_sum,0)=0 THEN NULL
                 ELSE ROUND(k.c_sale_total / m.c_task_sum, 4) END, ...
FROM (...) k LEFT JOIN #ttb_task_m_sum m ON ...

-- ② 最外层只做取值 + 格式化
IIF(@查询维度='月',
    CONVERT(VARCHAR(20), CAST(ROUND(MAX(cc.c_rate_m) * 100, 1) AS DECIMAL(10,1))) + '%',
    NULL) AS 月度任务完成率
```

## 排查流程（5 分钟）

1. **Navicat 能跑？** 能 → 问题在平台解析，往下走；不能 → 改 SQL
2. **数括号**：报错里 `")"` 的个数和你的括号配平对不上 → 平台拆坏了
3. **改一次后报错一字不差**（连顺序个数都一样）→ **立刻换方向**，说明你动的地方它根本没读到，它挂得更早
4. **查最外层 SELECT**：有没有嵌套聚合？`MAX(a)/MAX(b)` 聚合间除法？5 层以上嵌套？有就全挪进临时表
5. **`GROUP BY` 保持和原版逐字一致**，不要为新逻辑增删项

## 本次案例

给「商品销售（含任务）」报表加 9 个计算列（月度任务/日度任务/两个任务合计/销售金额合计/两个完成率/时间进度/进度差）。

- 第一次尝试：`SUM(SUM()) OVER` 和 `BETWEEN` 两处改掉 → **报错一模一样**（说明找错方向）
- 第二次尝试：新增 `#ttb_calc` 临时表 + `GROUP BY` 恢复逐字一致 → **通过**
- 验证：月维度 232 行、日维度 1099 行，新 9 列与 sqlA 逐行比对**零差异**

文件：`C:\work\tmp\plan-probe\商品销售_含任务_SQLB保守版_2026-11-17.sql`
完整手册：`docs/报表平台SQL报错排查手册-2026-11-17.md`

## 相关

- 同一批排查里发现的视图缺陷：`vw_gds_sale_record` 的 `ROW_NUMBER` 排序键并列导致查询结果漂移，见 `docs/问题报告-合同75537201销售数量漂移-2026-11-17.md`
- 该报表还依赖 `tbs_sd_gdsprv`（金额口径）与 `tb_md_plan`（任务口径），两者粒度不同，见 [[reference-sd-calibers]]
