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
   - 写一行或多行 `abr_engine_config`：每个「国家 × 学位 × 专业大类」一行（`default` 对应专业大类 `*`，`by_field` 的每个键各一行）；权重写 `weights` jsonb，`tier_cutoffs` 写档位阈值列，`version`、是否启用照抄；其余参数（`scoring`、`hard_thresholds`、`probability`、`benchmark_defaults`、`country_rules` 等）存为参数 jsonb。具体列名在 M3-05 / M3-07 任务卡里定稿，本文件结构是这两张卡的输入。
   - `benchmark_defaults` 不直接生成 `abr_program_benchmark`；录入项目（M2-08）时顾问给项目标难度档，系统据此生成来源为 `consultant_difficulty` 的参考线，之后由案例或官方统计覆盖。
2. **计算时（M3-05 / M3-06）**：Service 按学生的目标国家、学位、专业大类取启用版本的配置与项目参考线，组装成不可变的参数对象传给引擎；引擎依次做「子分 → 综合分 C → 硬门槛 → P → 分档 → 排序与组合 → 差距反算」。
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

