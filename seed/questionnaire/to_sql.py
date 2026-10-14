"""把 seed/questionnaire/questionnaire_*.json 转成可重复执行的种子脚本 sql/abroad/update_20261014_questionnaire_seed.sql。

用法（在仓库根目录）：python3 -I seed/questionnaire/to_sql.py
种子只导入为草稿（draft），不会启用；顾问定稿后由管理端「问卷版本」页启用。
"""
import json
import sys
from pathlib import Path

ROOT = Path(sys.argv[0]).resolve().parents[2]
SRC = sorted((ROOT / "seed/questionnaire").glob("questionnaire_*.json"))
OUT = ROOT / "sql/abroad/update_20261014_questionnaire_seed.sql"
TAG = "$qjson$"


def q(s):
    return "'" + (s or "").replace("'", "''") + "'"


rows = []
for path in SRC:
    d = json.loads(path.read_text(encoding="utf-8"))
    body = json.dumps(d, ensure_ascii=False, separators=(",", ":"))
    if TAG in body:
        sys.exit(f"{path.name} 中包含 {TAG}，请换一个 dollar-quote 标记")
    rows.append("  ({}, {}, {}, {}, {}, {}{}{})".format(
        q(d["code"]), int(d["version"]), q(d.get("version_label")), q(d["title"]),
        len(d["questions"]), TAG, body, TAG))
    print(f"{path.name}: {d['code']} v{d['version']}，{len(d['questions'])} 题")

sql = """-- ----------------------------------------------------------------------------
-- M3-01 问卷种子（由 seed/questionnaire/to_sql.py 从 seed/questionnaire/questionnaire_*.json 生成，不要手工编辑）
-- 只导入为草稿：已存在的同编码同版本若仍是草稿则用种子刷新，已启用或已停用的版本不受影响
-- ----------------------------------------------------------------------------
insert into abr_questionnaire (code, version, version_label, title, question_count, definition, publish_status, del_flag, create_by, create_time, remark)
select v.code, v.version, v.version_label, v.title, v.question_count, cast(v.definition as jsonb), 'draft', '0', 'admin', now(), '种子导入（seed/questionnaire）'
from (values
{}
) as v(code, version, version_label, title, question_count, definition)
on conflict (code, version) where del_flag = '0'
do update set version_label  = excluded.version_label,
              title          = excluded.title,
              question_count = excluded.question_count,
              definition     = excluded.definition,
              update_by      = 'admin',
              update_time    = now()
        where abr_questionnaire.publish_status = 'draft'
          and abr_questionnaire.definition is distinct from excluded.definition;
""".format(",\n".join(rows))

OUT.write_text(sql, encoding="utf-8")
print(f"已生成 {OUT.relative_to(ROOT)}")
