# 接口回归测试

针对运行中的后端执行，用于任务卡验收与回归。只用 Python 标准库。

前提：
- 后端已启动（默认 `http://localhost:8090`，可用环境变量 `ABR_BASE` 指向其他地址）；
- 数据库已执行 `sql/` 全量脚本与 `sql/abroad/` 增量脚本；
- 关闭登录验证码（测试库执行 `update sys_config set config_value='false' where config_key='sys.account.captchaEnabled'`），默认账号 admin / admin123。

**只能对测试库执行**：脚本会新增并删除院校、项目数据；AI 网关脚本会写调用日志、审核记录，并临时修改 `_selftest` Agent 的审核策略（结束时恢复）。

| 脚本 | 覆盖 |
| --- | --- |
| `m2_catalog_test.py` | M2-01 院校 / 项目 / 申请要求 / 字段来源；M2-03 国内高校搜索 |
| `m2_ai_gateway_test.py` | M2-04 / M2-05 AI 网关：Agent 配置、试用接口（mock 模型）、主备切换、Schema 重试与降级、输出过滤、去标识化、审核队列（通过 / 驳回 / 修改）、抽检、调用日志筛选与用量统计 |
| `m2_ai_prefill_test.py` | M2-06 项目 AI 预填（`catalog_extractor`，mock 模型）：本机样例页面的 found / not_found / discarded（引句不在原文即丢弃）、前端取值形状、调用日志、预填不写业务表；重定向、PDF（不是网页）、404（页面打不开）、403（需要登录或被拒绝）、超过 2 MB、15 秒超时、非 http(s)、每用户每分钟 5 次限流（脚本中途等待约 60 秒）；确认后保存（来源 origin=1 + 引句）读回；可选：内网地址拦截 |

```bash
python3 -I test/api/m2_catalog_test.py
ABR_BASE=http://localhost:8091 python3 -I test/api/m2_ai_gateway_test.py
```

`m2_ai_prefill_test.py` 在 127.0.0.1 的随机端口上起样例页面服务，被测实例必须允许抓取本机地址（**只限测试实例**，生产保持默认 false）：

```bash
java -jar zt-admin.jar --server.port=8093 --abroad.ai.fetch.allow-private-hosts=true ...
# 可选：再起一个默认配置（allow-private-hosts=false）的实例，验证本机、内网、云元数据地址被拦截；两个实例用不同的 Redis 库，避免共用限流计数
java -jar zt-admin.jar --server.port=8094 --spring.data.redis.database=4 ...
ABR_BASE=http://localhost:8093 ABR_BASE_STRICT=http://localhost:8094 python3 -I test/api/m2_ai_prefill_test.py
```

未设置 `ABR_BASE_STRICT` 时跳过拦截检查（单元测试 `AbrWebPageFetcherTest` 覆盖 IPv4 / IPv6 地址拦截与重定向逐跳校验）。

AI 评测命令（不经 HTTP，跑 `prompts/<agent>/<版本>/eval/` 的样例，报告写到 `./ai-eval/`，全部通过退出码 0）：

```bash
java -jar zt-admin/target/zt-admin.jar --abroad.ai.eval=_selftest
java -jar zt-admin/target/zt-admin.jar --abroad.ai.eval=catalog_extractor
```
