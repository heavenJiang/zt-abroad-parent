#!/bin/sh
# 构建并部署：后端镜像、管理后台与 C 端 H5 静态文件、增量脚本、启动全部服务
#   ./deploy.sh            全部
#   SKIP_BUILD=1 ./deploy.sh  只重启（不重新构建）
# 需要：Docker（含 compose 插件）；两个前端工程与本仓库放在同一父目录（../../zt-abroad-vue、../../zt-abroad-uniapp）
set -eu
cd "$(dirname "$0")"
[ -f .env ] || { echo "缺少 deploy/.env：cp .env.example .env 后填写" >&2; exit 1; }
. ./.env
for v in ADMIN_DOMAIN APP_DOMAIN WWW_DOMAIN API_DOMAIN POSTGRES_PASSWORD REDIS_PASSWORD TOKEN_SECRET; do
  eval "val=\${$v:-}"
  case "$val" in ""|请改为*) echo "deploy/.env 中 $v 未填写" >&2; exit 1;; esac
done
[ ${#TOKEN_SECRET} -ge 32 ] || { echo "TOKEN_SECRET 至少 32 位" >&2; exit 1; }

ROOT=$(cd .. && pwd)
WORK=$(cd ../.. && pwd)
mkdir -p site/admin site/app site/www backups certs
# 项目页在 M7-08 由后端生成；之前放一个占位页
[ -f site/www/index.html ] || printf '<!doctype html><meta charset="utf-8"><title>建设中</title><p>建设中</p>\n' > site/www/index.html

if [ -z "${SKIP_BUILD:-}" ]; then
  echo "== 构建后端镜像"
  docker build -t "zt-abroad-admin:${IMAGE_TAG:-latest}" --target admin "$ROOT"
  echo "== 构建管理后台"
  docker run --rm -v "$WORK/zt-abroad-vue":/src:ro -v abr-npm-cache:/root/.npm -v "$PWD/site/admin":/out -w /work node:20 \
    sh -c 'cp -r /src/. /work/ && npm ci --no-audit --no-fund && npm run build:prod && rm -rf /out/* && cp -r dist/. /out/'
  echo "== 构建 C 端 H5"
  docker run --rm -v "$WORK/zt-abroad-uniapp":/src:ro -v abr-npm-cache:/root/.npm -v "$PWD/site/app":/out -w /work node:20 \
    sh -c 'cp -r /src/. /work/ && npm ci --no-audit --no-fund && npm run build:h5 && rm -rf /out/* && cp -r dist/build/h5/. /out/'
fi

echo "== 启动数据库并执行增量脚本"
docker compose -f docker-compose.prod.yml up -d abr-postgres abr-redis
docker compose -f docker-compose.prod.yml exec -T abr-postgres sh -c 'until pg_isready -q; do sleep 1; done'
docker compose -f docker-compose.prod.yml exec -T -e POSTGRES_USER="$POSTGRES_USER" -e POSTGRES_DB="$POSTGRES_DB" abr-postgres sh /abroad-sql/migrate.sh

echo "== 启动全部服务"
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
