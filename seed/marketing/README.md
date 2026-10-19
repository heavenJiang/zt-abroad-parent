# 营销日历节点与消息模板草案（M0-10）

> AI 草拟，**待创始人确认**；确认前不作为上线依据。说明与待确认问题见 `doc/准备/M0-10-营销日历剧本与合规词表说明.md`。
> 剧本与时间窗草案已在 M6-01 写入 `sql/abroad/update_20261018_orchestrator.sql`（备注“草案（M0-10 待创始人确认）”），在管理端“获客运营 → 剧本 / 营销日历”修改，这里不重复。

## 文件

| 文件 | 内容 | 使用方 |
| --- | --- | --- |
| `calendar_nodes.csv` | 16 个日历节点（考研出分、国家线、美本 ED / RD、美高放榜、排名发布等）：节点编码、月日、学段、关联剧本、来源 | 确认后导入 `abr_calendar_entry`（entry_type = node），当天发出 `calendar_node` 事件 |
| `message_templates.json` | 12 个消息模板：关注提醒、截止前 30 / 7 天、要求变化、出分重测、再激活、邀约、预约确认 / 提醒 / 改约、欢迎语、退订确认 | M8-01 / M8-03 / M8-04 / M8-05 建模板表时导入 |

## calendar_nodes.csv 列

| 列 | 说明 |
| --- | --- |
| `node_code` | 节点编码，触发规则按它过滤（`event_filter: {"node_code": "..."}`） |
| `month_day` | 大致月日（MM-dd）；**每年按来源核对后改成当年日期** |
| `repeat_yearly` | Y 每年重复 |
| `degrees` | 适用学段（`abr_degree`），空为全部 |
| `playbook_code` | 关联剧本（填写时事件带上剧本编码），空为不关联 |
| `source_hint` | 核对日期的来源 |

院校自己的开放日、截止日不放日历，由院校库驱动（M8-05 关注提醒）。

## message_templates.json

- `kind`：`service` 服务通知（用户订阅或已发生的业务）/ `marketing` 营销消息；
- `audience`：`adult` 只发成年本人 / `adult_or_parent` 成年本人或家长（未成年学生的营销只发家长）；
- `channels`：`template` 服务号模板消息字段、`subscribe` 小程序订阅消息字段、`wecom` 企业微信、`sms` 短信；实际字段名以申请到的模板为准；
- `{变量}` 见文件内 `variables`，只取院校库、引擎输出、预约记录与配置。

所有模板已用 `seed/compliance/` 词表自检通过（无违禁词、无录取承诺、营销短信带“拒收请回复R”）。
