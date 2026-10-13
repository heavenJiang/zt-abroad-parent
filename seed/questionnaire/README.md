# 测评问卷定义（seed/questionnaire）

`questionnaire_v1.json` 是选校测评问卷的定义文件，由任务卡 M0-04 起草（**status = draft，顾问定稿前不得启用**）。定稿后由 M3-01 导入 `abr_questionnaire`（版本、题目定义 jsonb、是否启用）。管理端「问卷版本」页可以上传新版本，改问卷不需要发版。起草说明与待确认清单见 `doc/准备/M0-04-问卷初稿说明.md`。

## 顶层字段

| 字段 | 含义 |
| --- | --- |
| `code` / `version` / `status` | 问卷编码、整数版本号、`draft` / `active` / `retired` |
| `countries` / `degree_levels` | 覆盖的国家（US UK JP KR AU SG DE）与学位，题目通过 `options_ref` 引用 |
| `ux` | 时长目标（3–5 分钟）、每屏最多 4 题、自动保存、断点续答、登录时机等前端约定 |
| `consent` | 第 1 步之前的隐私同意；`separate_consents` 为上传简历前的单独同意 |
| `hidden_fields` | 学生看不到的字段：`channel_code`（渠道码）、`client_platform` |
| `steps` | 5 步（目标 / 学术 / 语言与标化 / 经历 / 偏好），每步分若干 `screens`，每屏列出题目 id |
| `questions` | 扁平题目列表，`step` 指向所属步骤 |
| `normalization` | 换算表：均分 → 百分制、英语成绩 → 雅思等效分、专业相关度（**均为草案，需顾问核对**） |
| `report_requirements` | 生成报告前必须有的最少答案 |
| `derived_fields` | 由答案计算出、写入 `abr_profile` 的字段 |

## 题目字段

| 字段 | 含义 |
| --- | --- |
| `id` | 稳定的 snake_case 标识，**发布后不得改名**（答案 jsonb 以它为键，旧测评要能读） |
| `step` / `order` | 所属步骤、步骤内顺序 |
| `title` / `help` / `placeholder` | 中文题干、帮助文字、输入提示；`title_variants` / `help_variants` 按条件替换文案 |
| `type` | 见下方「题型」 |
| `required` | 题目可见时是否必答；不可见的题目一律不校验 |
| `options` / `options_ref` / `options_source` | 静态选项（`value` + 中文 `label`）、引用顶层列表、动态来源（字典或规则）；有 `options_source` 时静态 `options` 只作兜底 |
| `validation` | `min` / `max` / `step` / `integer`、`min_selected` / `max_selected`、`min_length` / `max_length`、`regex`、`lte_field` |
| `visible_if` | 显示条件，见下方「条件语法」；不写表示始终显示 |
| `default` | 学生跳过时使用的值 |
| `skip_hint` | 选填题被跳过时，报告里「补充 XX 可让结果更准」的文案 |
| `prefill_from` | 简历解析（`resume_parser`）结果中的对应字段，只作为建议值，学生确认后才写入 |
| `est_seconds` | 估算作答秒数，用于控制总时长 |
| `on_answer` | 按选项触发的流程行为：`action`（`stop` 终止 / `continue`）、`save_answer`、提示页 `page` / `notice`、写入测评的 `flags`（如 `is_minor`、`exclude_from_referral_unlock`）。目前只有 `age_band` 使用 |
| `maps_to` | 题目对应的引擎输入维度，见下方「维度映射」 |
| `*_status` / `status` | 标注该处为草案、需顾问确认 |

## 题型

| type | 说明 | 答案形状 |
| --- | --- | --- |
| `single` | 单选（点选） | `"value"` |
| `multi` | 多选 | `["v1", "v2"]` |
| `number` | 数字输入 | `12` |
| `text` | 文本输入 | `"..."` |
| `select-search` | 输入后搜索选择；可配 `search.endpoint`（如国内高校搜索）或 `local_options` + `keywords`；`special_options` 提供「海外本科」「搜不到」等出口；`allow_free_text` 允许自由填写 | `{ "kind", "university_id", "name" }` 或 `{ "value", "text", "matched" }` |
| `score-with-type` | 先选分数类型再填分数（均分的分制、英语考试种类、GRE / GMAT），每种类型各自定义取值范围 | `{ "type", "total", "min_sub"?, "max"? }` |
| `exp-summary` | 经历卡片：有 / 无 → 段数 → 级别 → 是否相关；子字段在 `fields` 中定义，子字段条件用 `{ "field": ... }` | `{ "has", "count", "level", "relevant", ... }` |
| `file` | 文件上传，`file` 定义格式、大小、份数，`parse` 定义解析 Agent 和预填目标 | `[{ "file_id", "kind" }]` |

## 条件语法（`visible_if`）

- 叶子条件：`{ "q": "<题目 id>", "path": "<答案内字段，可选>", "op": "<运算符>", "value": <值> }`
- exp-summary 子字段之间：`{ "field": "<子字段 key>", "op": ..., "value": ... }`
- 组合：`{ "all": [ ... ] }`（且）、`{ "any": [ ... ] }`（或）、`{ "not": { ... } }`
- 运算符：`eq`、`ne`、`in`、`not_in`、`contains`（多选包含某值）、`contains_any`、`answered`、`not_answered`、`gt`、`gte`、`lt`、`lte`
- 示例：`{ "q": "target_countries", "op": "contains", "value": "JP" }` 表示目标国家含日本时才显示 JLPT 题
- 题目被隐藏时：答案留在草稿里（学生改回来不用重填），**提交时剔除**，不参与归一化和引擎计算
- 某一屏的题目都不可见且该屏标了 `show_if_any_visible` 时，整屏跳过

## 维度映射（`maps_to`）

| 字段 | 含义 |
| --- | --- |
| `dimension` | 维度编码，与产品设计「智能选校测评算法 · 输入维度」对应：`academic.school_tier`（本科院校层次）、`academic.gpa`（GPA / 均分）、`academic.major_relevance`（专业相关度）、`academic.class_rank`（排名）、`language.english`（雅思 / 托福 / 多邻国）、`test.gre_gmat`（GRE / GMAT）、`soft.research`（科研）、`soft.work`（实习 / 工作）、`soft.awards`（竞赛、项目、证书）、`pref.*`（偏好）；另有多国家适配所需的 `language.japanese`、`language.korean`、`path.de_aps`、`path.timeline`，以及组合键 `combo.country` / `combo.degree` / `combo.field` 和不进引擎的 `meta.*`（含 `meta.age_band`） |
| `role` | `score`（进综合分）、`hard_threshold`（只做门槛，如语言）、`combo_key`（选择「国家 × 学位 × 专业大类」权重组合）、`filter_and_sort` / `sort`（偏好，不进竞争力分）、`path`（申请路径）、`context`、`prefill`、`review_only`、`attribution`、`eligibility`（适用资格，如年龄段，不进引擎） |
| `normalize` | 答案 → `abr_profile` 标准字段的规则（`rule`、`params`、`table_ref`、`output`） |

## 归一化分工

1. **问卷归一化**（本文件，M3-03 实现）：答案 → 标准字段。均分按 `normalization.gpa_to_percent` 换算为百分制（有百分制均分时优先使用）；雅思、托福、PTE、多邻国按 `normalization.english_to_ielts` 换算为雅思等效分（`floor_table`：取阈值不超过原始分的最高分段）；院校层次由国内高校搜索结果（`abr_cn_university`）自动带出，学生看不到层次；专业相关度按本科专业与目标方向的细分值和大类比较得出。
2. **引擎归一化**（`abr_engine_config`，不在本文件）：标准字段 → 0–100 子分、权重、门槛、档位。

换算表都标了 `"status": "草案，需顾问核对"`，在顾问按官方最新资料核对并改为定稿之前，不得当作权威数据使用。
