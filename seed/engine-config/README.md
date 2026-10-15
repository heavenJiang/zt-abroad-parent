# 选校引擎参数种子（engine-config）

M0-06 产出的**第一批引擎参数草案**：每个「国家 × 学位」一份 JSON，供 M3-05（综合分、录取可能性、分档）实现时作为参数输入、M3-07（引擎配置管理）导入为 `abr_engine_config` / `abr_program_benchmark` 的第一个版本。

- 依据：产品设计「智能选校测评算法」§1–§8（`doc/产品/00-产品设计.md`），表结构口径见 `doc/研发/02-架构设计.md` 与 `doc/研发/00-RuoYi落地对照.md`。
- 说明与算例：`doc/准备/M0-06-引擎参数草案说明.md`。
- **全部数字都是草案**。每组数字旁边都有 `notes`（或 `xxx_notes`），写明来源：`设计文档 §x`（取自产品设计）或 `AI 草拟，需顾问确认`。任何数字都不代表真实院校的录取数据。

## 文件

| 文件 | 国家 | 学位 | 上线批次（§8 建议顺序） |
| --- | --- | --- | --- |
| `uk_master.json` | 英国 | 授课型硕士 | 1 |
| `au_master.json` | 澳洲 | 授课型硕士 | 1 |
| `sg_master.json` | 新加坡 | 授课型硕士 | 1 |
| `hk_master.json` | 中国香港 | 授课型硕士 | 1（2026-10-14 加入，先复制新加坡草案） |
| `us_master.json` | 美国 | 硕士（含商科、理工覆盖） | 2 |
| `jp_master.json` | 日本 | 修士（含路径推荐） | 3 |
| `kr_master.json` | 韩国 | 硕士 | 3 |
| `de_master.json` | 德国 | 硕士（含课程匹配度、APS） | 3 |

文件名为 `<国家代码小写>_<学位>.json`。首批学位是硕士：§2 的示意权重全部是硕士，《冷启动与种子用户》的种子人群是申请英、澳、新授课型硕士的大三大四学生。美国本科按 §8 另建 `us_bachelor.json`，不在本批。

## 状态与版本

| 字段 | 取值 | 说明 |
| --- | --- | --- |
| `status` | `draft` → `confirmed` | AI 生成时为 `draft`；顾问逐项确认后改为 `confirmed`，并填写 `confirmed_by`（工号或姓名缩写）与 `confirmed_at`（`yyyy-MM-dd`） |
| `version` | `<CONFIG_CODE>-<yyyy.MM>-draft.<n>` | 任何数字改动都要升 `n`；确认后去掉 `draft`，如 `UK_MASTER-2026.11.1` |

只有 `confirmed` 的文件可以导入为启用版本；`draft` 只能导入为未启用版本，供引擎测试台试算。顾问修改数字时，同时把对应 `notes` 改为 `顾问确认 yyyy-MM-dd`（或写明依据），不要留下“AI 草拟”的旧说明。

## 结构

顶层字段：

| 字段 | 内容 | 设计依据 |
| --- | --- | --- |
| `schema` | 固定 `abr-engine-config/1.0`，结构不兼容时升级 | — |
| `config_code` / `country` / `country_name` / `degree` | 配置编码、国家 / 地区代码（US/UK/AU/SG/HK/JP/KR/DE）、学位（`master`） | §2、§8 |
| `launch_batch` | 上线批次 1/2/3 | §8 上线顺序 |
| `dimensions` | 进入综合分的维度键与中文名；`not_weighted` 列出不进权重的项 | §1、§2、§8 |
| `weights` | `default` 一组权重 + `by_field` 按专业大类的覆盖 | §2 |
| `scoring` | 每个维度原始值 → 0–100 子分的公式与参数 | §1 |
| `hard_thresholds` | 硬门槛检查项、兜底值、不达标动作 | §3 |
| `probability` | 公式、默认稳定度 `a_default`、参考线整体偏移 `t_offset`、取整规则 | §4、§8 |
| `tier_cutoffs` | 冲 / 稳 / 保分界 0.35 / 0.70，学生端只显示档位 | §4 |
| `benchmark_defaults` | 冷启动参考线：五档难度 D1–D5 各自的默认 T | §4、§7 |
| `portfolio` | 默认 2 冲 + 4 稳 + 2 保与组合约束 | §5 |
| `preference` | 偏好匹配分：重要程度、预算档上限、汇率与生活费、排名 / 学制换算、大都市城市列表（M3-06 新增，draft.2） | §5 |
| `gap_analysis` | 差距反算的目标档、可改变维度、各动作所需月数 | §6 |
| `calibration` | 重拟合所需样本量、每季校准检查口径 | §7 |
| `country_rules` | 本国特殊处理（见下文） | §8 |

### 维度（`dimensions.keys`，所有文件相同、顺序固定）

| 键 | 含义 | 档案输入（问卷归一化输出，见 `seed/questionnaire`；字段即 `engine.BackgroundProfile`，M3-03 已实现，主要字段在 `abr_profile` 单独建列） |
| --- | --- | --- |
| `academic` | 院校层次 + GPA（含排名加分）；§2 权重表第一列 | `undergrad_tier`、`gpa_percent`、`class_rank` |
| `major_relevance` | 专业相关度 | `major_relevance`（same/related/cross）、`missing_prereq_count` |
| `test_scores` | GRE / GMAT | `std_test_type`（`gre` / `gmat_focus` / `gmat_classic`）、`std_test_total` |
| `research` | 科研 | `research_level`、`research_count`、`research_relevant` |
| `work_experience` | 实习 / 工作 | `work_months`、`work_company_tier`（`top` / `normal`）、`work_relevant` |
| `other_soft` | 竞赛、项目、证书 | `awards_level`、`awards_count` |
| `course_match` | 课程匹配度，§8 德国新增；其他国家权重为 0 | `course_match_ratio`（成绩单解析） |

语言（雅思等效分）不进权重，只做硬门槛（§2）；偏好（国家、预算、排名 vs 就业、城市、学制）只用于筛选和档内排序（§1、§5）。`undergrad_tier` 取值与问卷一致：`c9`、`985`、`211`、`double_first_class`、`non_key`（双非）、`overseas`（海本），分别对应 `seed/cn-university` 的 `C9`、`985`、`211`、`双一流`、`双非`；问卷中的 `unknown`（搜不到学校）按 `non_key` 计算。

### 权重（`weights`）

- 每组权重必须包含上表全部 7 个键，取值 ≥ 0，**和为 1**（容差 1e-9）。
- 取用顺序：`by_field[专业大类]` 存在则用它，否则用 `default`。目前只有美国有 `business`、`stem` 两个覆盖（§2 示意行）。
- 专业大类取问卷的 `target_field_group`：`business`、`stem`、`health`、`humanities_social`、`arts`；`undecided` 用 `default`。院校库学科大类字典（建议 `abr_subject_area`）定稿后如有改名，同步修改 `by_field` 的键。
- `test_scores` 的 `not_required_policy = renormalize`：某项目不要求标化时，计算该项目的 C 时去掉此维度，其余权重按比例放大到和为 1（§1「不要求的项目不计入」）。

### 子分（`scoring`）

每个维度给出 `formula`（文字公式）和公式里用到的参数。`clamp` 表示截断到 0–100。要点：

| 维度 | 公式要点 |
| --- | --- |
| academic | `tier_base[层次] + gpa_slope × (百分制均分 − tier_reference_gpa[层次]) + rank_bonus[排名档]`；`tier_reference_gpa` 是 §1 的「院校层次对应的门槛均分」，各国不同 |
| major_relevance | 本专业 100 / 相近 70 / 跨专业 35，缺一门先修课扣 10，最多扣 3 门；同时输出风险项 |
| test_scores | 相对参考中位数的位置：`60 + 斜率 × (分数 − 参考值)`，GRE、GMAT Focus、GMAT 旧版各有参考值；未考用 `missing_score` |
| research | 最高级别分 × 相关度系数（相关 1.0 / 不相关 0.5），每多一段 +5，无科研 20 |
| work_experience | 有效月数 = 累计月数 × 公司系数（头部 1.0 / 一般 0.7）× 相关度系数；`20 + 80 × min(有效月数 / 饱和月数, 1)` |
| other_soft | 最高级别分，每多一项 +5，无 20 |
| course_match | `100 × 课程匹配比例`（仅德国） |

GPA 从 4 分制等换算为百分制、托福 / PTE / 多邻国换算为雅思等效分，属于归一化（M3-03，换算表在 `seed/questionnaire`），不在本文件中。

### 硬门槛（`hard_thresholds`）

1. 数据来源：项目库 `abr_requirement` 有记录时**只用项目库**；缺记录时才用本文件的兜底值，并在结果上打「要求待核实」标记（`unverified_flag_when_default_used`）。
2. 检查项：`language`（雅思等效总分与最低小分，按项目难度档兜底）、`min_gpa_by_undergrad_tier`（难度档 × 本科层次的最低均分矩阵；`undergrad_tiers_not_accepted_by_difficulty` 只由顾问或项目库填写，AI 不预填）、`prerequisites`、`work_years`、`cross_major`（三者只取项目库）。
3. 任一项不达标：项目不进入冲 / 稳 / 保，归入「补足后可申」（`on_fail = needs_improvement`）。
4. 各国补充：英国 / 澳洲的课前语言（差距 ≤ `max_overall_gap` 且项目允许时标注“可读语言班”而非不达标）、日本 / 韩国按授课语言区分、德国的课程匹配兜底与 APS，见 `country_rules`。

### 录取可能性与分档

```
T_eff = T + probability.t_offset
P     = 1 / (1 + exp(-a × (C − T_eff)))
冲：P < 0.35；稳：0.35 ≤ P ≤ 0.70；保：P > 0.70
```

- **T 与 a 的取用顺序**：项目自己的参考线 `abr_program_benchmark`（来源按可信度：`own_cases` → `official_stats` → `consultant_difficulty`）→ 项目没有参考线时，用该项目被标的难度档 D1–D5 在 `benchmark_defaults.difficulty_levels` 中的 T，`a` 用 `probability.a_default`。每个结果都记录 T 的来源（§4「每条 Tⱼ 标明来源」）。
- 难度档说明里的排名区间只是帮顾问定档的参考，不是对任何真实院校的判断。
- 取整：C 保留 2 位、P 保留 4 位，`HALF_UP`；分档用取整后的 P，保证同一输入同一输出。
- 学生端只显示冲 / 稳 / 保（`expose_probability_to_student = false`），P 只在管理端和引擎测试台出现。

### 国家特例（`country_rules`，§8）

| 国家 | 键 | 作用 |
| --- | --- | --- |
| 美国 | `soft_background_emphasis`、`conservative_tiering`、`test_policy`、`undergraduate_model` | 软背景权重高；a 小并整体抬高 T（`t_offset`）；标化按项目政策；本科另建模 |
| 英国 | `tier_gpa_table_is_core`、`pre_sessional`、`rolling_admission_hint` | 分层均分为核心硬门槛、不容差；课前语言标注；滚动录取递交时机提示 |
| 澳洲 | `safety_country`、`pre_sessional` | 作为保底国家：组合时优先从澳洲项目补保底位 |
| 新加坡 | `high_reference_line`、`mix_application_hint` | 参考线高；保底档不足时提示与英、澳混申 |
| 日本 | `teaching_language_thresholds`、`path_recommendation`、`service_tasks` | 日语 / 英语项目分别设语言门槛；输出推荐路径（直申修士 / 研究生）；增加研究计划书与联系导师任务 |
| 韩国 | `teaching_language_thresholds`、`scholarship_match` | 韩语 / 英语项目分别设语言门槛；奖学金匹配只影响档内排序 |
| 德国 | `course_match`、`aps`、`uni_assist`、`teaching_language_thresholds` | 课程匹配度维度与兜底门槛；APS 未办理归入「补足后可申」并倒推时间线；uni-assist 标记；德语授课只按项目要求 |

## 引擎如何读取

引擎包 `com.ygb.abroad.engine` 是纯 Java（开发规范 14.1）：**不读文件、不访问数据库**。读取链路如下：

1. **导入（M3-07）**：管理端「引擎配置」导入本目录的 JSON。导入前做校验（见下节），通过后：
   - 写一行 `abr_engine_config`：每个「国家 × 学位」一个版本一行，整份 JSON 存入 `config` jsonb（专业大类权重就在 `weights.by_field` 里，不拆行，保证一个版本的参数一起生效、一起留痕）；`config_code`、`country`、`degree`、`version`、`status`（存为 `file_status`）从 JSON 取。（M3-07 定稿，替代早先“每个专业大类一行”的设想。）
   - `benchmark_defaults` 不生成 `abr_program_benchmark` 行：项目没有自己的参考线时，引擎在计算时按项目难度档直接取默认 T，来源记为 `consultant_difficulty`；有案例、官方统计或顾问单独定的 T 时，在管理端「参考线」页为该项目录入。
2. **计算时（M3-05 / M3-06 / M3-07）**：`AbrSelectionServiceImpl` 按学生的目标国家、学位取启用版本的配置，按专业大类选候选项目并取项目参考线，交给引擎；引擎依次做「子分 → 硬门槛 → 综合分 C → P → 分档 → 排序与组合 → 差距反算」。
3. **留痕**：`abr_assessment_result` 记录所用配置的 `version`（引擎版本）、每个项目的 P、档位、T 的来源与差距 jsonb。
4. **测试台（M3-08）**：用同一套参数批量跑 `seed/engine-cases/` 的案例，输出命中率（见该目录 README）。

### 导入校验规则

- JSON 可解析；`schema` 为 `abr-engine-config/1.0`；`dimensions.keys` 与上表一致。
- 每组权重：键齐全、取值 ≥ 0、和为 1；`course_match` 只在德国大于 0。
- `0 < reach_below < safety_above < 1`；`expose_probability_to_student` 必须为 `false`。
- `a_default > 0`；D1–D5 的 T 严格递减。
- 每组数字带 `notes`；`status = confirmed` 时不应再有“AI 草拟”字样（导入时给出警告）。

本目录的文件在 M0-06 生成时已用 Python 按上述规则校验通过（全部可解析、权重和为 1）。

## 实现（M3-03）

- 子分由 `com.ygb.abroad.engine.SubScoreCalculator` 按本目录各文件的 `scoring` 节计算，参数缺失时直接报错（不默认）；单元测试用 `uk_master.json` 复现 M0-06 说明文档 §3.1 的算例（88 / 100 / 20 / 48 / 60），并对全部 8 个文件跑同一个学生检查子分在 0–100 内。
- 归一化（答案 → 背景档案）由 `engine.AnswerVisibility`（剔除隐藏题）、`engine.NormalizationRules`（问卷 normalization 节）、`engine.ProfileNormalizer` 完成；`engine` 包行覆盖率由 JaCoCo 在 `mvn verify` 时检查，低于 90% 构建失败。


## 实现（M3-04）

硬门槛由 `com.ygb.abroad.engine.HardThresholdChecker` 实现，输入为背景档案、项目事实 `engine.ProgramFacts`（由 `service.impl.AbrEngineInputs` 从 `abr_program` + 当季 `abr_requirement` 组装）和该项目所在国家的配置根节点。

- **取值按项**：项目库某一项有值时只用项目库；该项为空（或 `min_gpa` 里没有学生所在层次）时，才用本文件按难度档的兜底值，并打 `requirement_unverified`（要求待核实）。项目库标了“官网不设均分线”（`gpa_no_cutoff = Y`，M3-08 测试台发现后补充）时，均分不套兜底，直接记达标。项目未定难度档、又没有项目库要求时，该项记为“未判定”，打 `requirement_missing`。
- **结果**：`pass`（进入综合分与分档）、`on_fail` 的值（`needs_improvement`，补足后可申）、`excluded`（不在学生考虑范围：日本 / 韩国项目为日语 / 韩语授课，而学生选了“只考虑英语授课”）。`missing` 列出不达标的检查项，即“缺什么”。
- **检查项**：

| 键 | 规则 |
| --- | --- |
| `language_en` | 英语授课（缺省）。项目写了学生所考考试（托福 / PTE / 多邻国）的总分时直接比原始分；否则比雅思等效总分与最低小分。差距 ≤ `country_rules.pre_sessional.max_overall_gap`（英、澳）时记 `pre_sessional`（可读语言班），不判不达标；项目库 `accept_pre_sessional` 为“不接受”时不适用，为“未知”时照常适用并加 `pre_sessional_unverified`。没考也估不出 → 未判定 + `language_pending`；预估分照常比较 + `language_estimated`；非雅思考试没有小分换算 → 只比总分 + `sub_score_unchecked` |
| `language_ja` / `language_ko` | 日语 / 韩语授课：JLPT / TOPIK 等级，项目库 `other_language.jlpt|topik.level` 优先，否则 `teaching_language_thresholds` 按难度档 |
| `language_de` | 德语授课：问卷 v1 不采集德语水平，只列出项目库要求，记未判定 + `german_to_verify` |
| `tier_accepted`、`gpa` | 不接收的本科层次；层次对应最低均分（≥ 即达标，不容差）。层次待核定按双非计算 + `tier_unknown`；未填均分 → 未判定 |
| `prerequisites` | 只取项目库。缺先修课：硬性要求判不达标，否则记风险；问卷 v1 不采集所修课程，非本专业时记风险 `prereq_to_verify` |
| `work_years` | 只取项目库，按全职月数比较 |
| `cross_major` | 只对跨专业学生检查：不接受 → 不达标；未知或无记录 → 风险 `cross_major_unknown` |
| `aps`、`course_match` | 德国：APS 未办理（不了解按未办理）→ 不达标，审核中 → 达标 + `aps_in_progress`；课程匹配度低于兜底比例 → 不达标，未解析成绩单 → 未判定 |

- 风险项和未判定项都**不筛掉项目**，只给顾问提示。可接受的语言差距只在国家规则里配置（目前英、澳），其他国家项目即使标“接受语言班”也不适用。
- 测试复现 M0-06 说明文档 §3.3（项目 A / B 通过）与 §3.5（D1 项目因均分不够归入补足后可申；雅思差 0.5 在英国标“可读语言班”），并对 8 个国家各跑正、反、边界样例。

## 实现（M3-05）

入口为 `com.ygb.abroad.engine.SelectionEngine`：每个候选项目依次做「子分（按国家缓存）→ 硬门槛 → 综合分 C → P → 分档」，结果按传入顺序返回，并记录所用配置的 `version`。

- **综合分**（`CompositeScorer`）：权重取 `weights.by_field[专业大类]`，没有时取 `default`；缺子分（如德国未解析成绩单的 `course_match`）、项目不要求标化（`test_requirement = none` 且 `not_required_policy = renormalize`）的维度从计算中去掉，其余权重按比例放大，放大后的权重随结果留痕。
- **P 与分档**（`AdmissionEstimator`）：T、a 取项目参考线（`ProgramBenchmark`，可只给 T 或只给 a），否则 T 取难度档默认值、来源记为 `default_source`，a 取 `a_default`；`T_eff = T + t_offset`。指数用 `StrictMath`，P 按 `p_scale` 取整后分档。项目既无参考线又未定难度档时分组为 `unrated`（待顾问定档）。
- **分组**：通过硬门槛的按档位分为 `reach` / `match` / `safety` / `unrated`；未通过的为 `needs_improvement`（C、P 仍计算，供 M3-06 差距反算），不在考虑范围的为 `excluded`。
- **快测**（`SelectionEngine.quick`）：只取 `undergrad_school`、`gpa`、`target_countries`、`target_field` 四题归一化；综合分只用学术维度（院校层次 + 均分，权重放大为 1）；硬门槛只查院校层次 / 均分与语言（未采集时记“语言待考”），先修课、工作年限、跨专业、APS、课程匹配度不查，结果带 `quick_mode`。
- 测试复现 M0-06 §3.2（C = 81.00）、§3.4（A：P = 0.4551 → 稳；B：P = 0.8787 → 保）、§3.5（D1：P = 0.2210 → 冲；补足后 C = 85.55、P = 0.3915 → 稳）；300 个项目一次评估在 1 秒内。
- 配置、参考线、结果的入库（`abr_engine_config`、`abr_program_benchmark`、`abr_assessment_result`）在 M3-07 随配置管理一起完成，届时学生提交测评后自动计算。

## 实现（M3-06）

入口为 `com.ygb.abroad.engine.RecommendationPlanner.plan(档案, SelectionEngine 的评估结果, 各国配置)`，输出推荐方案。

- **偏好匹配分**（`PreferenceRanker`，`preference` 节）：预算（全程费用 ≤ 预算上限为 100，否则 0）、排名（QS 名次折线）、城市（是否在大都市列表）、学制（月数折线）各 0–100；学生选为“最看重”的项权重 `importance.high`，其余 `normal`，加权平均。院校库缺学费 / 汇率、名次、城市时该项不计分，给提示码；学生选“就业优先”时因院校库没有就业数据不计分。
- **档内排序**：偏好分高在前（无偏好分按 50），其次 P 高在前，最后项目ID。
- **推荐组合**：按首选国家（第一个目标国家）配置的 `portfolio.default_mix` 从各档取前 N 个；超预算的不进组合；保底位优先取学生目标中“保底国家”（`country_rules.safety_country`，澳洲）的项目；组合里没有首选国家的保底时，换入首选国家最好的保底，没有则提示。
- **提示**：某档不足（`tier_shortage`）、首选国家没有保底、超预算未进组合、新加坡 / 中国香港保底不足时建议混申（`mix_application_hint`）、院校库无就业数据；快测结果带 `quick_academic_only`（学生端必须展示“仅按院校层次和均分估算”）。
- **差距反算**（`GapAnalyzer`，`gap_analysis` 节）：冲刺校反算进入稳档所需 C* = T_eff + ln(p / (1 − p)) / a（向上取到 2 位），在标化（只对已考 GRE 的学生试算 +10 分）、科研（新增课题组经历）、实习（新增 3 个月相关实习）中枚举组合，取能达到 C* 的最小组合（动作最少，其次月数最少）；月数相加后与“距入学月数 − 递交提前量”比较判断是否来得及。补足后可申的项目：雅思每次 +0.5 试算到语言达标、德国 APS 按提前月数办理，其余项（均分、层次、先修课等）标为不能靠短期动作补足。
- 测试复现 M0-06 §3.5：D1 项目 C* = 84.57（差 3.57），课题组经历 +1.75、3 个月实习 +2.80，组合 +4.55 → 进入稳档，共 7 个月。
- 未实现：韩国奖学金匹配（院校库没有“是否有奖学金”字段）、日本推荐路径（留给 M4-03 日本路径测评）、英国滚动录取递交时机提示（需申请日历，M4-11）。

## 实现（M3-07）

- **表**：`abr_engine_config`（配置版本，草稿 / 启用 / 已停用，同一国家 × 学位只有一个启用版本）、`abr_program_benchmark`（每个项目一条参考线：T、可选的 a、来源、样本数）、`abr_assessment_result`（每份测评一条结果，重算时覆盖）。脚本 `sql/abroad/update_20261015_engine.sql`；本目录的草案由 `to_sql.py` 生成 `update_20261015_engine_seed.sql`，只导入为草稿。
- **启用规则**：只有 `status = confirmed` 的文件能启用（草案只能导入为草稿，供 M3-08 测试台试算）。导入时按上文「导入校验规则」检查，并用一个示例背景对 D1–D5 五个示例项目试算一遍（完整测评 + 快测 + 推荐方案），缺参数直接报错；确认后仍有“AI 草拟”说明、缺确认人、版本号含 draft 时给警告。
- **计算**：学生在 C 端提交测评（`POST /app/assessment/{id}/submit`：检查必答题、剔除隐藏题答案）后自动计算；顾问可在「测评记录」重新计算（`POST /abroad/assessment/{id}/compute`，同时重新归一化）。候选项目 = 学生目标国家中已启用配置的、学位一致、专业大类一致（未定方向时不限）的正常项目，申请要求取最新申请季；没有启用配置的国家记入 `skipped_countries`，不计算。
- **留痕**：`abr_assessment_result` 存所用配置版本、各项目的分组 / 档位 / C / P / T 及来源 / 硬门槛明细 / 偏好分、推荐组合、差距反算、提示和各国子分。P 只在管理端显示；C 端结果接口在 M4-05 做，只返回档位。

## 实现（M3-08）

测试台 `com.ygb.abroad.engine.CaseBench` 按 `seed/engine-cases/README.md` 第 4 节口径统计：档位命中率（每档、整体、3×3 混淆矩阵）、严重错档、补足后可申与硬门槛误判、硬门槛与官网一致率（案例填了 `expected_threshold` 时）、按引擎档位的实际录取率（每组少于 10 行标“样本不足”）。案例 CSV 由 `AbrEngineCaseCsv` 解析，均分与英语分未换算时按启用问卷的换算表换算。

## 实现（M3-09）

- 引擎按学段选策略（`engine.DegreeStrategy`）：`master` 为硬门槛 + 综合分 + 分档（已实现）；`secondary`、`bachelor` 为学术匹配、`phd` 为匹配清单不分档，均在学段扩展路线 M11–M13 实现。未实现的学段：学生提交后结果为 `consultant` 模式（不计算、提示码 `degree_consultant_review`），配置文件 `degree` 为这些学段时不能导入。
- 计算用的学段：问卷中的目标学位答案优先，未答时用测评首屏选择的学段（`abr_assessment.degree`）。
