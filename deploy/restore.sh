#!/bin/sh
# 从备份恢复数据库（会覆盖当前数据，执行前先停后端）：
#   ./restore.sh backups/abroad_20261015_033000.dump
# 每月在测试环境做一次恢复演练（架构设计「备份」）
set -eu
cd "$(dirname "$0")"
[ $# -eq 1 ] && [ -f "$1" ] || { echo "用法：./restore.sh <备份文件>" >&2; exit 1; }
. ./.env
docker compose -f docker-compose.prod.yml stop abr-admin
docker compose -f docker-compose.prod.yml exec -T abr-postgres \
  pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner < "$1"
docker compose -f docker-compose.prod.yml start abr-admin
echo "已从 $1 恢复"
