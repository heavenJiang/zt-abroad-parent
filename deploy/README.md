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

## C 端登录（M4-04）

服务号认证（M0-01）后在 `.env` 中填写，`./deploy.sh` 生效；未填写时 C 端登录提示“服务号登录尚未开通”，匿名使用不受影响。

| 变量 | 说明 |
| --- | --- |
| `WECHAT_OA_APP_ID` | 服务号 AppID（公众平台 → 设置与开发 → 基本配置） |
| `WECHAT_OA_APP_SECRET` | 服务号 AppSecret（只放 `.env`，不要提交到仓库） |
| `WECHAT_MINI_APP_ID` / `WECHAT_MINI_APP_SECRET` | 小程序 AppID / AppSecret（订阅消息用；未填时小程序订阅消息渠道为“未开通”） |
| `WECOM_CORP_ID` / `WECOM_AGENT_ID` / `WECOM_SECRET` | 企业微信企业 ID、自建应用 AgentId 与 Secret（员工提醒、熔断通知用；员工的企业微信账号须与后台登录名一致） |

另需在公众平台「网页授权域名」中填写 `APP_DOMAIN`。绑定手机号的短信服务随 M0-01 开通后接入（`AbrSmsClient`），之前绑定手机号会提示“短信服务尚未开通”。

## 文字识别（MA-15）

`abr-ocr` 是内嵌的开源文字识别服务（`ocr/`：RapidOCR 3.9.2 + PaddleOCR 开源模型，默认 PP-OCRv6，ONNX Runtime CPU），用于图片 / 扫描版简历与成绩单、offer 截图。`deploy.sh` 会构建镜像 `zt-abroad-ocr`，模型在构建时下载并固定，运行时不访问外网；容器只在内网提供给 `abr-admin`（`ABR_OCR_URL=http://abr-ocr:9003`），不映射端口，图片不出服务器。

- 国内服务器构建慢或失败时换 pip 镜像源：`PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple ./deploy.sh`
- 资源：镜像约 460 MB，常驻内存约 300–500 MB，CPU 识别一页 0.5–2 秒（并发请求排队）
- 停用：去掉 `abr-admin` 的 `ABR_OCR_URL` 即可，学生上传图片时会提示手动填写

## 项目自动采集（M2-08）

定时任务 `abrCrawlTask.discover()`（每天 02:30）与 `abrCrawlTask.fetch()`（每 2 分钟）在生产默认启用，按 robots.txt 低频抓取各校官网项目页；进度在管理后台「院校库 → 项目采集」查看。配置真实大模型（`ABR_AI_DEEPSEEK_API_KEY` 或 `ABR_AI_QWEN_API_KEY`，并在「Agent 配置」把 `catalog_extractor` 的主模型改为对应模型）后，均分要求等复杂字段由大模型抽取，项目才能满足上架条件。

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

## 熔断演练（M10-03）

测试环境上线前或改动熔断规则后，用 `deploy/drill/breaker-drill.sh` 演练退订激增、投诉、平台违规提示三种情况（只在消息渠道为 mock 的测试环境运行）。用法与验证点见 `doc/研发/06-熔断演练.md`。
