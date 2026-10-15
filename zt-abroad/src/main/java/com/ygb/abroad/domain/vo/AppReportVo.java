package com.ygb.abroad.domain.vo;

import java.util.List;

/**
 * C 端：测评报告结构化部分（产品文档「报告页结构」7 个区块中由引擎与院校库给出的部分；AI 解读在 M4-06 补上）
 *
 * 只含学生可见字段：不含综合分、录取概率、参考线、权重与院校层次；子分只用于画像条的长度，取整到 5。
 *
 * @param assessmentId 测评ID
 * @param mode full 引擎已出结果 / consultant 该学段或方向由顾问评估 / pending 结果未生成（顾问会重新计算）
 * @param headline 一句话定位（模板句；AI 解读上线后替换）
 * @param notices 提示码（minor 未成年提示；no_referral_unlock 不展示分享解锁类激励；degree_consultant_review 由顾问评估）
 * @param minorNotice 未成年提示文字（来自问卷，非未成年为 null）
 * @param profile 竞争力画像（首选国家 / 地区口径）
 * @param tiers 选校结果：冲刺 / 主攻 / 保底（固定三组，代表校来自推荐组合）
 * @param unratedCount 待顾问定档的项目数
 * @param shortcomings 最关键的 1–2 个短板
 * @param needsImprovement 补足后可申
 * @param needsImprovementCount 补足后可申的项目总数
 * @param hints 方案提示码（tier_shortage、no_safety_in_first_choice、mix_application、employment_data_missing）
 * @param skippedCountries 暂由顾问评估的目标国家 / 地区（未开放）
 * @param skipHints 跳过的题目：补充后结果更准
 * @param dataNote 数据说明
 * @author ygb
 */
public record AppReportVo(Long assessmentId, String mode, String headline, List<String> notices, String minorNotice, List<Dimension> profile,
        List<Tier> tiers, int unratedCount, List<Shortcoming> shortcomings, List<Program> needsImprovement, int needsImprovementCount,
        List<String> hints, List<AppQuickResultVo.CountryRef> skippedCountries, List<SkipHint> skipHints, DataNote dataNote)
{
    /**
     * 画像的一个维度
     *
     * @param key 维度（academic / major_relevance / test_scores / research / work_experience / other_soft）
     * @param label 名称
     * @param level 0–100，取整到 5（只用于画条，不展示数值）
     */
    public record Dimension(String key, String label, int level)
    {
    }

    /**
     * 一个档位
     *
     * @param tier reach / match / safety
     * @param count 该档项目总数
     * @param programs 代表校（推荐组合中的项目）
     */
    public record Tier(String tier, int count, List<Program> programs)
    {
    }

    /**
     * 一个项目
     *
     * @param programId 项目ID
     * @param schoolName 院校
     * @param programName 项目
     * @param country 国家 / 地区代码
     * @param countryLabel 国家 / 地区名称
     * @param met 已达标的要求（gpa / language_en / tier_accepted 等检查项）
     * @param missing 未达标的要求（补足后可申）
     * @param flags 提示码（requirement_unverified 要求待核实、pre_sessional 需读语言班、language_pending 语言待考）
     * @param upgrade 冲刺校：补上哪些动作可进入主攻（动作编码；没有可行组合时为空）
     * @param upgradeInTime 冲刺校：按距入学时间是否来得及（未知为 null）
     * @param fixes 补足后可申：补足动作（如 ielts_plus_0_5 × 次数）
     * @param verifiedDate 项目信息核对日期（yyyy-MM-dd；从未核对为 null）
     * @param unverified 超过 90 天未核实（不展示要求数值）
     */
    public record Program(Long programId, String schoolName, String programName, String country, String countryLabel, List<String> met,
            List<Gap> missing, List<String> flags, List<String> upgrade, Boolean upgradeInTime, List<Fix> fixes, String verifiedDate,
            boolean unverified)
    {
    }

    /**
     * 未达标的要求
     *
     * @param item 检查项
     * @param required 要求（展示用；院校背景类、超过 90 天未核实时为 null）
     * @param actual 学生情况（院校背景类为 null）
     */
    public record Gap(String item, String required, String actual)
    {
    }

    /**
     * 补足动作
     *
     * @param item 检查项
     * @param action 动作编码（ielts_plus_0_5 / do_aps；不能靠短期动作补足时为 null）
     * @param steps 次数
     * @param months 所需月数
     */
    public record Fix(String item, String action, int steps, Integer months)
    {
    }

    /**
     * 短板
     *
     * @param kind upgrade 冲刺进入主攻 / unlock 补足后可申变为可申
     * @param actions 动作编码（按动作顺序）
     * @param steps 动作次数（雅思提高 0.5 的次数；其他为 1）
     * @param programCount 受益的项目数
     * @param months 所需月数（最多的一个项目）
     * @param inTime 是否来得及（全部来得及为 true，有来不及的为 false，未知为 null）
     */
    public record Shortcoming(String kind, List<String> actions, int steps, int programCount, Integer months, Boolean inTime)
    {
    }

    /**
     * 跳过的题目
     *
     * @param question 题目 id
     * @param title 题目
     * @param hint 补充提示（问卷 skip_hint）
     */
    public record SkipHint(String question, String title, String hint)
    {
    }

    /**
     * 数据说明
     *
     * @param verifiedFrom 报告中项目最早的核对日期
     * @param verifiedTo 最近的核对日期
     * @param unverifiedCount 超过 90 天未核实或从未核实的项目数
     * @param computedTime 结果计算时间（yyyy-MM-dd HH:mm）
     */
    public record DataNote(String verifiedFrom, String verifiedTo, int unverifiedCount, String computedTime)
    {
    }
}
