#!/bin/sh
# ----------------------------------------------------------------------------
# 按文件名顺序（字节序，LC_ALL=C，不受系统语言排序规则影响）执行留学业务增量脚本（sql/abroad/update_*.sql）
#
# 脚本都可重复执行，因此每次部署全部重跑一遍即可，无需记录执行到哪个版本：
#   PGHOST=localhost PGPORT=5433 PGUSER=postgres PGPASSWORD=postgre PGDATABASE=abroad sql/abroad/migrate.sh
#
# docker compose 空库首次启动时也会自动执行（挂载为 /docker-entrypoint-initdb.d/03_abroad.sh，
# 此时连接参数取 POSTGRES_USER / POSTGRES_DB，脚本目录取 ABR_SQL_DIR）。
# ----------------------------------------------------------------------------
set -e

dir="${1:-${ABR_SQL_DIR:-$(dirname "$0")}}"
# 重复执行时 "already exists, skipping" 之类的 NOTICE 不输出
export PGOPTIONS="${PGOPTIONS:--c client_min_messages=warning}"

for f in $(ls "$dir"/update_*.sql | LC_ALL=C sort); do
  echo "执行 $f"
  psql -v ON_ERROR_STOP=1 -q ${POSTGRES_USER:+--username "$POSTGRES_USER"} ${POSTGRES_DB:+--dbname "$POSTGRES_DB"} -f "$f"
done
