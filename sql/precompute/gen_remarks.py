# -*- coding: utf-8 -*-
"""
从《01_create_dw_schema.sql》解析全部列名（权威），
再从《销售报表1预计算表设计方案.md》匹配中文含义，
生成 sp_addextendedproperty 备注 SQL。保证建表列与备注一一对应。
输出: C:\work\MyTeamWork\sql\precompute\02_column_remarks.sql
"""
import re
import io

DDL = r"C:\work\MyTeamWork\sql\precompute\01_create_dw_schema.sql"
SRC = r"C:\work\销售报表1预计算表设计方案.md"
OUT = r"C:\work\MyTeamWork\sql\precompute\02_column_remarks.sql"

# ---- 1. 从建表 SQL 解析列名（排除 CONSTRAINT/主键行） ----
with io.open(DDL, encoding="utf-8") as f:
    ddl = f.read()

# 只截取 rpt_sale_detail_precompute 的 CREATE TABLE 块（到第一个 ");" 结束）
block = ddl.split("CREATE TABLE dw.rpt_sale_detail_precompute (")[1]
block = block.split(");")[0]

cols = []
for line in block.splitlines():
    m = re.match(r"^\s*([a-z][a-z0-9_]*)\s+(NUMERIC|VARCHAR|NVARCHAR|DATE|DATETIME|BIGINT|INT)\b", line, re.I)
    if m and "CONSTRAINT" not in line:
        cols.append(m.group(1))

# ---- 2. 从设计文档解析 列名 -> 含义 ----
sec = open(SRC, encoding="utf-8").read()
sec = sec.split("#### A. ETL元数据（10列）")[1].split("### 3.3 索引设计")[0]

meaning = {}
for line in sec.splitlines():
    line = line.strip()
    if not line.startswith("|"):
        continue
    cells = [c.strip() for c in line.strip("|").split("|")]
    if len(cells) < 4:
        continue
    col = cells[1].strip()
    if not re.match(r"^[a-z][a-z0-9_]*$", col):
        continue
    meaning[col] = re.sub(r"`", "", cells[-1].strip())

# ---- 3. 生成 SQL ----
lines = []
lines.append("/* =====================================================================")
lines.append(" * 预计算表列备注 — 由脚本自动生成，勿手工修改")
lines.append(" * 列清单与 01_create_dw_schema.sql 严格一致")
lines.append(" * ===================================================================== */")
lines.append("")
lines.append("EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'销售报表1预计算结果表（每天凌晨2:00由引擎预计算，前端直查本表）', @level0type=N'SCHEMA', @level0name=N'dw', @level1type=N'TABLE', @level1name=N'rpt_sale_detail_precompute';")
lines.append("")

missing = []
for col in cols:
    m = meaning.get(col)
    if not m:
        missing.append(col)
        m = col  # 兜底：用列名
    lines.append(
        "EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'%s', "
        "@level0type=N'SCHEMA', @level0name=N'dw', @level1type=N'TABLE', "
        "@level1name=N'rpt_sale_detail_precompute', @level2type=N'COLUMN', @level2name=N'%s';"
        % (m.replace("'", "''"), col)
    )

with io.open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(lines))

print(f"建表列总数: {len(cols)}，已生成备注: {len(lines)-2} 条")
print(f"设计文档未匹配（用列名兜底）: {missing if missing else '无'}")
