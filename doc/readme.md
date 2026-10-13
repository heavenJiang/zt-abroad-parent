# 本地数据库与运行

推荐用项目根目录的 `docker compose up -d --build` 一键启动（PostgreSQL + Redis + 后端），端口与 zt-vision-parent 错开：

| 服务 | 容器 | 宿主机端口 |
| --- | --- | --- |
| PostgreSQL 15（库名 `abroad`） | `abr-postgres` | 5433 |
| Redis | `abr-redis` | 6380 |
| 后端 zt-admin | `abr-admin` | 8090 |

空库首次启动自动执行 `sql/zt_20261008_postgre.sql` + `sql/quartz_postgre.sql`；留学业务增量脚本见 `sql/abroad/README.md`。

只起数据库、在 IDE 里运行后端时：

```bash
docker compose up -d abr-postgres abr-redis
# 然后在 IDE 运行 zt-admin 的 ZtApplication（application-druid.yml 默认连 localhost:5433/abroad）
```

常用命令：

```bash
docker compose logs -f abr-admin    # 看日志
docker compose restart abr-admin    # 重新打包后重启
docker compose stop                 # 停止
docker compose down                 # 删除容器（数据保留在 abr-pgdata 卷）
```
