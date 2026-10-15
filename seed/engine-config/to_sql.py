"""把 seed/engine-config/*_master.json 转成可重复执行的种子脚本 sql/abroad/update_20261015_engine_seed.sql。

用法（在仓库根目录）：python3 -I seed/engine-config/to_sql.py
种子只导入为草稿（draft），不会启用；顾问确认（status 改为 confirmed、升版本）后在管理端「引擎配置」页导入并启用。
"""
import json
import sys
from pathlib import Path

ROOT = Path(sys.argv[0]).resolve().parents[2]
SRC = sorted((ROOT / "seed/engine-config").glob("*_master.json"))
OUT = ROOT / "sql/abroad/update_20261015_engine_seed.sql"
TAG = "$ejson$"


def q(s):
    return "'" + (s or "").replace("'", "''") + "'"


rows = []
for path in SRC:
    d = json.loads(path.read_text(encoding="utf-8"))
    body = json.dumps(d, ensure_ascii=False, separators=(",", ":"))
    if TAG in body:
        sys.exit(f"{path.name} 中包含 {TAG}，请换一个 dollar-quote 标记")
    rows.append("  ({}, {}, {}, {}, {}, {}{}{})".format(
        q(d["config_code"]), q(d["country"]), q(d["degree"]), q(d["version"]), q(d["status"]), TAG, body, TAG))
    print(f"{path.name}: {d['config_code']} {d['version']}（{d['status']}）")

sql = """-- ----------------------------------------------------------------------------
-- M3-07 引擎参数种子（由 seed/engine-config/to_sql.py 从 seed/engine-config/*_master.json 生成，不要手工编辑）
-- 只导入为草稿：已存在的同编码同版本若仍是草稿则用种子刷新，已启用或已停用的版本不受影响
-- ----------------------------------------------------------------------------
insert into abr_engine_config (config_code, country, degree, version, file_status, config, publish_status, del_flag, create_by, create_time, remark)
select v.config_code, v.country, v.degree, v.version, v.file_status, cast(v.config as jsonb), 'draft', '0', 'admin', now(), '种子导入（seed/engine-config）'
from (values
{}
) as v(config_code, country, degree, version, file_status, config)
on conflict (config_code, version) where del_flag = '0'
do update set country     = excluded.country,
              degree      = excluded.degree,
              file_status = excluded.file_status,
              config      = excluded.config,
              update_by   = 'admin',
              update_time = now()
        where abr_engine_config.publish_status = 'draft'
          and abr_engine_config.config is distinct from excluded.config;
""".format(",\n".join(rows))

OUT.write_text(sql, encoding="utf-8")
print(f"已生成 {OUT.relative_to(ROOT)}")
