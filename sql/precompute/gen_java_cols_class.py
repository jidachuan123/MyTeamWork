# -*- coding: utf-8 -*-
"""
从 _java_cols.txt（Python 生成的裸常量代码段）生成 PrecomputeCols.java 常量类。
另从 01_create_dw_schema.sql 解析列类型，生成 SQL_TYPES 映射（setNull 用）。
只做：补引号 + 组装类结构。常量内容本身已在上一环节校验（COLS:208 / CH:125 / ACC_EQ:44 / NULL_COLS:23）。
"""
import re, io, sys

SRC = r'C:\work\MyTeamWork\sql\precompute\_java_cols.txt'
DDL = r'C:\work\MyTeamWork\sql\precompute\01_create_dw_schema.sql'
OUT = r'C:\work\MyTeamWork\provider-service\src\main\java\com\demo\provider\precompute\PrecomputeCols.java'

src = open(SRC, encoding='utf-8').read()

# ---- 解析 COLS 数组 ----
m = re.search(r'private static final String\[\] COLS = \{(.*?)\};', src, re.S)
cols = re.findall(r'^\s*(\w+),?\s*$', m.group(1), re.M)

# ---- 解析 CH / ACC_EQ / NULL_COLS ----
ch_items = re.findall(r'CH\.put\((\w+),\s*([^)]+)\);', src)
acc_items = re.findall(r'ACC_EQ\.put\((\w+),\s*(\w+)\);', src)
null_items = re.findall(r'NULL_COLS\.add\((\w+)\);', src)

# ---- 从建表 SQL 解析列类型（SQL Server 类型 -> JDBC Types） ----
sql = open(DDL, encoding='utf-8').read()
m = re.search(r'CREATE TABLE dw\.rpt_sale_detail_precompute.*?\((.*?)\)\s*(?:WITH|;|GO)', sql, re.S)
body = m.group(1)
JDBC = {
    'NUMERIC': 'java.sql.Types.NUMERIC', 'DECIMAL': 'java.sql.Types.NUMERIC',
    'VARCHAR': 'java.sql.Types.VARCHAR', 'NVARCHAR': 'java.sql.Types.NVARCHAR',
    'CHAR': 'java.sql.Types.CHAR',
    'DATE': 'java.sql.Types.DATE', 'DATETIME': 'java.sql.Types.TIMESTAMP',
    'BIGINT': 'java.sql.Types.BIGINT', 'INT': 'java.sql.Types.INTEGER',
    'BIT': 'java.sql.Types.BIT',
}
col_types = {}
for line in body.splitlines():
    line = line.strip().rstrip(',')
    t = re.match(r'([a-z_0-9]+)\s+((?:NUMERIC|DECIMAL|VARCHAR|NVARCHAR|CHAR|DATE|DATETIME|BIGINT|INT|BIT)\S*)', line, re.I)
    if t:
        base = re.match(r'([A-Z]+)', t.group(2), re.I).group(1).upper()
        col_types[t.group(1)] = JDBC.get(base, 'java.sql.Types.NULL')
missing_types = [c for c in cols if c not in col_types]

def q(s):
    return '"' + s + '"'

lines = []
A = lines.append
A('package com.demo.provider.precompute;')
A('')
A('import java.util.Arrays;')
A('import java.util.HashMap;')
A('import java.util.HashSet;')
A('import java.util.List;')
A('import java.util.Map;')
A('import java.util.Set;')
A('')
A('/**')
A(' * 销售报表1预计算表 dw.rpt_sale_detail_precompute 列映射常量。')
A(' * 由脚本从建表 SQL / 设计文档自动生成，勿手改；加列时重新跑生成脚本。')
A(' *')
A(' * COLS：208 列（含 id，INSERT 时跳过自增列）')
A(' * CH：英文列名 -> 引擎最终 SELECT 返回的中文别名')
A(' * ACC_EQ：acc_* 累计列 -> 等价 C_* 列（记账日期模式下引擎 UPDATE C_ = C_ACC_，最终 SELECT 返回 C_）')
A(' * NULL_COLS：引擎不返回/预留列，写入 NULL')
A(' * SQL_TYPES：列名 -> JDBC 类型（setNull 用；mssql-jdbc 对 Types.NULL 会发 varbinary，必须指定具体类型）')
A(' */')
A('public final class PrecomputeCols {')
A('    private PrecomputeCols() {}')
A('')
A('    /** 208 列（含 id），顺序即写入顺序 */')
A('    public static final List<String> COLS = Arrays.asList(')
for i, c in enumerate(cols):
    comma = ',' if i < len(cols) - 1 else ''
    A('        ' + q(c) + comma)
A('    );')
A('')
A('    /** 英文列名 -> 引擎返回的中文 key（125 条） */')
A('    public static final Map<String, String> CH = new HashMap<>();')
A('    static {')
for k, v in ch_items:
    A('        CH.put(' + q(k) + ', ' + q(v.strip()) + ');')
A('    }')
A('')
A('    /** acc_* -> 等价 C_* 列（44 条） */')
A('    public static final Map<String, String> ACC_EQ = new HashMap<>();')
A('    static {')
for k, v in acc_items:
    A('        ACC_EQ.put(' + q(k) + ', ' + q(v.strip()) + ');')
A('    }')
A('')
A('    /** 写入 NULL 的列（23 条：ext_01~10 预留 + 引擎不返回的 raw/acc 比率列 + sale_qty_dq） */')
A('    public static final Set<String> NULL_COLS = new HashSet<>();')
A('    static {')
for k in null_items:
    A('        NULL_COLS.add(' + q(k) + ');')
A('    }')
A('')
A('    /** 列名 -> JDBC 类型（setNull 必须用具体类型；mssql-jdbc 对 Types.NULL 发送 varbinary(8000) 会触发 date 列隐式转换错误） */')
A('    public static final Map<String, Integer> SQL_TYPES = new HashMap<>();')
A('    static {')
for c in cols:
    A('        SQL_TYPES.put(' + q(c) + ', ' + col_types.get(c, 'java.sql.Types.NULL') + ');')
A('    }')
A('}')
A('')

out = '\n'.join(lines)
with open(OUT, 'w', encoding='utf-8', newline='\n') as f:
    f.write(out)

print('COLS:', len(cols), '| CH:', len(ch_items), '| ACC_EQ:', len(acc_items), '| NULL_COLS:', len(null_items))
print('SQL_TYPES:', len(col_types), '条（缺类型列:', missing_types if missing_types else '无', '）')
print('written:', OUT)
