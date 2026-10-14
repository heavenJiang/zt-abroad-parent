# 留学业务数据库脚本

- 全量初始化（系统表、Quartz）在上级目录：`zt_20261008_postgre.sql`、`quartz_postgre.sql`，docker compose 空库首次启动自动执行。
- 留学业务（`abr_` 表、`abr_` 字典、`abroad:` 菜单与按钮、参数）的增量脚本放在本目录，命名 `update_<yyyyMMdd>_<内容>.sql`，由 `migrate.sh` 按文件名顺序全部执行（脚本可重复执行，已执行过的不受影响）：

  ```bash
  PGHOST=localhost PGPORT=5433 PGUSER=postgres PGPASSWORD=postgre PGDATABASE=abroad sql/abroad/migrate.sh
  ```

- docker compose 空库首次启动时会自动执行 `migrate.sh`；已有数据的库在拉取新脚本后手动执行一次。测试 / 生产环境部署时在启动新版本应用前执行。
- 同一天有多个脚本时，文件名排序即执行顺序；后一个脚本依赖前一个时，注意 `<内容>` 部分的字母顺序（必要时加序号，如 `update_20261014_1_ai.sql`）。

- 脚本必须可重复执行（`create table if not exists`、`add column if not exists`、`on conflict do nothing`），表和字段都写 `comment on`，种子数据后重置序列；菜单 ID 从 2000 起由序列分配，不写显式 ID。
- 提交前在临时库上实际执行验证（见 `doc/开发规范.md` 第 6、7 章）。
