# 留学业务数据库脚本

- 全量初始化（系统表、Quartz）在上级目录：`zt_20261008_postgre.sql`、`quartz_postgre.sql`，docker compose 空库首次启动自动执行。
- 留学业务（`abr_` 表、`abr_` 字典、`abroad:` 菜单与按钮、参数）的增量脚本放在本目录，命名 `update_<yyyyMMdd>_<内容>.sql`，按日期顺序手动执行：

  ```bash
  psql -h localhost -p 5433 -U postgres -d abroad -f sql/abroad/update_20261020_catalog.sql
  ```

- 脚本必须可重复执行（`create table if not exists`、`add column if not exists`、`on conflict do nothing`），表和字段都写 `comment on`，种子数据后重置序列；菜单 ID 从 2000 起由序列分配，不写显式 ID。
- 提交前在临时库上实际执行验证（见 `doc/开发规范.md` 第 6、7 章）。
