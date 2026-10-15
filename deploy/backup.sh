#!/bin/sh
# 数据库备份：pg_dump 自定义格式，按日期命名，保留 BACKUP_KEEP_DAYS 天
#   backup.sh once       立即备份一次
#   backup.sh schedule   常驻：每天 BACKUP_AT（北京时间 HH:MM）备份一次（compose 的 abr-backup 服务使用）
# 连接参数取 PGHOST / PGUSER / PGPASSWORD / PGDATABASE；备份写到 /backups
# 异地备份（对象存储）在开通对象存储后加上传步骤（M0-01）；恢复见 restore.sh
set -eu

DIR=/backups
KEEP=${BACKUP_KEEP_DAYS:-30}
AT=${BACKUP_AT:-03:30}

once() {
  mkdir -p "$DIR"
  file="$DIR/abroad_$(date +%Y%m%d_%H%M%S).dump"
  pg_dump -Fc -f "$file.part"
  mv "$file.part" "$file"
  echo "$(date '+%F %T') 备份完成 $file（$(du -h "$file" | cut -f1)）"
  find "$DIR" -name 'abroad_*.dump' -type f -mtime +"$KEEP" -print -delete | sed 's/^/已删除过期备份 /'
}

case "${1:-once}" in
  once)
    once
    ;;
  schedule)
    echo "每天 $AT 备份，保留 $KEEP 天"
    last=""
    while true; do
      today=$(date +%F)
      if [ "$(date +%H:%M)" = "$AT" ] && [ "$last" != "$today" ]; then
        once || echo "$(date '+%F %T') 备份失败" >&2
        last=$today
      fi
      sleep 30
    done
    ;;
  *)
    echo "用法：backup.sh once|schedule" >&2
    exit 1
    ;;
esac
