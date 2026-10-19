# 部署（生产 / 测试环境）

一台服务器跑全部服务：PostgreSQL 15、Redis 7、后端 `zt-admin`、Nginx（按域名分流）、每日备份。本地开发仍用仓库根目录的 `docker-compose.yml`。

## 首次部署

前提：服务器装好 Docker（含 compose 插件）；`zt-abroad-parent`、`zt-abroad-vue`、`zt-abroad-uniapp` 三个仓库放在同一父目录；四个域名已备案并解析到服务器。

```bash
cd zt-abroad-parent/deploy
cp .env.example .env && vi .env     # 填域名、数据库 / Redis 密码、TOKEN_SECRET（≥ 32 位随机串）
./deploy.sh                         # 构建后端镜像与两个前端、执行增量脚本、启动全部服务
```

之后更新：拉取三个仓库的新代码后再执行 `./deploy.sh`（增量脚本可重复执行）；只想重启用 `SKIP_BUILD=1 ./deploy.sh`。

首次启动后用管理员账号登录管理后台，**立即修改默认密码**（admin / admin123）。

## 域名分流（《模块拆分·端划分》）

| 域名 | 内容 | 代理 |
| --- | --- | --- |
| `ADMIN_DOMAIN` | 管理后台静态文件 | `/prod-api/**` → 后端；**拒绝** `/prod-api/app/**`、`/prod-api/callback/**`、`/prod-api/druid/**` |
| `APP_DOMAIN` | C 端 H5 | **只代理** `/app/**`；其余路径回退到 `index.html` |
| `WWW_DOMAIN` | 公开项目页（后端生成到 `site/www/`，见下文） | 不代理接口 |
| `API_DOMAIN` | — | 只代理 `/app/**`（小程序）与 `/callback/**`（第三方回调） |
| 其他 / IP 直连 | — | 直接断开（444） |

数据库、Redis、后端都不对外开放端口，只有 Nginx 监听 80 / 443。生产配置里关闭了 Druid 监控台和接口文档；数据库、Redis 密码与员工令牌密钥只从 `.env` 注入（开发规范第 8 章）。

## 公开项目页（M7-08）

后端把院校库渲染成静态 HTML，写到 `site/www/`（容器内 `/home/zt/site`），Nginx 只读提供，不经过 Spring。定时任务 `abrSiteTask.refresh()` 每 5 分钟检查院校库与日期，有变化时只重写变化的页面并更新 `sitemap.xml`；管理后台「获客运营 → 公开项目页」可查看生成记录、预览页面、手动重新生成。首次启动后几分钟内会替换占位页。

收录提交（可选，`.env` 中填写后 `./deploy.sh`）：

| 变量 | 说明 |
| --- | --- |
| `SITE_BAIDU_TOKEN` | 百度搜索资源平台 → 普通收录 → API 提交的令牌（站点须先在平台验证） |
| `SITE_INDEXNOW_KEY` | IndexNow 密钥（必应等；8–128 位字母、数字、短横线，生成器自动写入站点根目录的 `{key}.txt`） |

未填写时不提交，只生成站点地图（可在各平台后台手动提交 `https://WWW_DOMAIN/sitemap.xml`）。

## HTTPS（上线前必须，小程序只能请求 HTTPS）

1. 证书放到 `deploy/certs/`（如 `admin.pem` / `admin.key`，按域名命名）。
2. 在 `nginx/templates/abroad.conf.template` 每个 server 增加：

   ```nginx
   listen 443 ssl;
   ssl_certificate     /etc/nginx/certs/<域名>.pem;
   ssl_certificate_key /etc/nginx/certs/<域名>.key;
   ```

   并把 80 端口改为 `return 301 https://$host$request_uri;`。
3. `docker compose -f docker-compose.prod.yml up -d --force-recreate abr-nginx`。

## 备份与恢复

- `abr-backup` 服务每天 `BACKUP_AT`（北京时间，默认 03:30）执行 `pg_dump -Fc` 到 `deploy/backups/abroad_<日期>.dump`，保留 `BACKUP_KEEP_DAYS` 天（默认 30）。
- 立即备份：`docker compose -f docker-compose.prod.yml exec abr-backup sh /usr/local/bin/backup.sh once`
- 恢复（会覆盖当前数据，脚本会先停后端）：`./restore.sh backups/abroad_<日期>.dump`
- 每月在测试环境做一次恢复演练（架构设计「备份」）。
- **异地备份待做**：开通对象存储（M0-01）后在 `backup.sh` 的 `once` 里加上传步骤；在那之前请定期把 `deploy/backups/` 拷到服务器以外。

## 尚未完成（依赖服务器与账号，M0-01）

- 测试环境自动部署：服务器就绪后在 CI 中加部署步骤（SSH 到测试机执行 `git pull && ./deploy.sh`），密钥放 CI 机密变量。
- 对象存储异地备份、HTTPS 证书（见上）。
