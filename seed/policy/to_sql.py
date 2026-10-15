"""把隐私政策草案（doc/准备/M0-08-隐私政策（草案）.md）转成协议版本种子 sql/abroad/update_20261015_policy_seed.sql。

用法（在仓库根目录）：python3 -I seed/policy/to_sql.py
- 去掉文件开头给内部看的起草说明（第一个 --- 之前的引用块）与附录三（界面文案草案），只保留标题与正文；
- 导入为版本 0.1-draft、is_draft = Y、启用（测试环境需要可同意的版本）；律师审阅定稿后由管理端导入正式版本并启用，草案自动停用。
- 已存在的同编码同版本只在内容变化时刷新（且仍是草案时）。
"""
import sys
from pathlib import Path

ROOT = Path(sys.argv[0]).resolve().parents[2]
SRC = ROOT / "doc/准备/M0-08-隐私政策（草案）.md"
OUT = ROOT / "sql/abroad/update_20261015_policy_seed.sql"
TAG = "$pjson$"
CODE, VERSION = "abr_privacy_policy", "0.1-draft"

text = SRC.read_text(encoding="utf-8")
lines = text.splitlines()
title = lines[0].lstrip("# ").strip()
body_start = next(i for i, l in enumerate(lines) if l.strip() == "---") + 1
body_lines = lines[body_start:]
# 附录三「同意界面与勾选项文案（草案）」是给研发的界面文案，不对用户展示；其后的附录顺延编号
start = next(i for i, l in enumerate(body_lines) if l.startswith("## 附录三"))
end = next(i for i in range(start + 1, len(body_lines)) if body_lines[i].strip() == "---")
body_lines = body_lines[:start] + body_lines[end + 1:]
body_lines = [l for l in body_lines if l.strip() != "附录三：同意界面与勾选项文案（草案）"]
body_lines = [l.replace("附录四：", "附录三：", 1) if l.lstrip("# ").startswith("附录四：") else l for l in body_lines]
# 正文中“文案见附录三”的引用改为指向页面上的勾选项
body_lines = [l.replace("（文案见附录三）", "（勾选项文案在对应页面展示）") for l in body_lines]
body = "\n".join(body_lines).strip() + "\n"
if TAG in body:
    sys.exit(f"正文包含 {TAG}")

sql = f"""-- ----------------------------------------------------------------------------
-- M4-01 隐私政策草案种子（由 seed/policy/to_sql.py 从 doc/准备/M0-08-隐私政策（草案）.md 生成，不要手工编辑）
-- 草案未经律师审阅，只用于测试环境；正式上线前导入律师定稿版本并启用
-- ----------------------------------------------------------------------------
insert into abr_policy (policy_code, version, title, content, is_draft, publish_status, effective_date, del_flag, create_by, create_time, remark)
select '{CODE}', '{VERSION}', '{title}', {TAG}{body}{TAG}, 'Y',
       case when exists (select 1 from abr_policy p where p.policy_code = '{CODE}' and p.publish_status = 'active' and p.del_flag = '0')
            then 'draft' else 'active' end,
       current_date, '0', 'admin', now(), '种子导入（M0-08 草案，未经律师审阅）'
on conflict (policy_code, version) where del_flag = '0'
do update set title = excluded.title, content = excluded.content, update_by = 'admin', update_time = now()
        where abr_policy.is_draft = 'Y' and abr_policy.content is distinct from excluded.content;
"""
OUT.write_text(sql, encoding="utf-8")
print(f"已生成 {OUT.relative_to(ROOT)}：{title}，正文 {len(body)} 字符")
