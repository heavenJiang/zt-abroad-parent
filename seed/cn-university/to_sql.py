"""把 cn_university.csv 转成可重复执行的种子脚本 sql/abroad/update_20261013_cn_university_seed.sql。

用法（在仓库根目录）：python3 -I seed/cn-university/to_sql.py
"""
import csv
import sys
from pathlib import Path

ROOT = Path(sys.argv[0]).resolve().parents[2]
SRC = ROOT / "seed/cn-university/cn_university.csv"
OUT = ROOT / "sql/abroad/update_20261013_cn_university_seed.sql"
TIER = {"C9": "c9", "985": "985", "211": "211", "双一流": "double_first_class"}


def q(s):
    return "'" + s.replace("'", "''") + "'"


def yn(v):
    return "'Y'" if v.strip() == "1" else "'N'"


rows = []
with SRC.open(encoding="utf-8") as f:
    for r in csv.DictReader(f):
        campus = "'1'" if r["note"].startswith("分校区") else "'0'"
        rows.append("  ({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})".format(
            q(r["name"]), q(r["aliases"]), q(r["province"]), q(r["city"]),
            yn(r["is_c9"]), yn(r["is_985"]), yn(r["is_211"]), yn(r["is_double_first_class"]),
            q(TIER[r["tier"]]), campus, q(r["source"]), q(r["note"])))

sql = """-- ----------------------------------------------------------------------------
-- M2-03 国内高校层次种子数据（由 seed/cn-university/to_sql.py 从 cn_university.csv 生成，不要手工编辑）
-- 可重复执行：按高校全称 upsert；顾问在管理端修改过的记录会被种子覆盖，修改请改 CSV 后重新生成
-- ----------------------------------------------------------------------------
insert into abr_cn_university (university_name, aliases, province, city, is_c9, is_985, is_211, is_double_first_class, tier, campus_type, source, note, status, create_by, create_time)
select v.*, '0', 'admin', now()
from (values
{}
) as v(university_name, aliases, province, city, is_c9, is_985, is_211, is_double_first_class, tier, campus_type, source, note)
on conflict (university_name) do update set
  aliases = excluded.aliases, province = excluded.province, city = excluded.city,
  is_c9 = excluded.is_c9, is_985 = excluded.is_985, is_211 = excluded.is_211,
  is_double_first_class = excluded.is_double_first_class, tier = excluded.tier,
  campus_type = excluded.campus_type, source = excluded.source, note = excluded.note,
  update_by = 'admin', update_time = now();
""".format(",\n".join(rows))
OUT.write_text(sql, encoding="utf-8")
print(f"{len(rows)} rows -> {OUT.relative_to(ROOT)}")
