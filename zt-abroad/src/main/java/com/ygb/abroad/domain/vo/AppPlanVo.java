package com.ygb.abroad.domain.vo;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * C 端：我的选校单（按最新背景档案逐个项目计算档位与逐项达标；不含综合分、录取概率、参考线与院校层次）
 *
 * @param basis 计算口径（full 完整档案 / quick 只有院校与均分，仅按学术估算 / none 背景信息不足，不计算）
 * @param items 项目（按加入顺序）
 * @param tierCounts 各分组的项目数（reach / match / safety / unrated / needs_improvement / excluded）
 * @param hints 档位分布提示
 * @param missingInfo 背景信息不足时建议补充的题目 id（basis 为 none 时）
 * @author ygb
 */
public record AppPlanVo(String basis, List<Item> items, Map<String, Integer> tierCounts, List<Hint> hints, List<String> missingInfo)
{
    /**
     * 选校单中的一个项目
     *
     * @param programId 项目ID
     * @param schoolName 院校
     * @param programName 项目
     * @param country 国家 / 地区代码
     * @param countryLabel 国家 / 地区名称
     * @param status ok 已计算 / offline 已下线 / consultant 学段或国家暂未开放、由顾问评估 / need_profile 补充背景后显示
     * @param tier 当前分组（reach / match / safety / unrated 待顾问定档 / needs_improvement 补足后可申 / excluded 不符合申请条件；未计算为 null）
     * @param tierAtAdd 加入时的分组（来自快测或报告；无为 null）
     * @param tierChanged 当前分组与加入时不同
     * @param checks 逐项达标
     * @param flags 提示码（requirement_unverified 要求待核实、pre_sessional 需读语言班、language_pending 语言待考）
     * @param verifiedDate 项目信息核对日期
     * @param unverified 超过 90 天未核实（不展示要求数值）
     * @param source 加入来源（quick / assessment / program / tool）
     * @param addedTime 加入时间
     */
    public record Item(Long programId, String schoolName, String programName, String country, String countryLabel, String status, String tier,
            String tierAtAdd, boolean tierChanged, List<Check> checks, List<String> flags, String verifiedDate, boolean unverified, String source,
            Date addedTime)
    {
    }

    /**
     * 单项要求
     *
     * @param item 检查项（gpa / language_en / tier_accepted 等）
     * @param outcome met 达标 / not_met 未达标 / pre_sessional 读语言班可达标 / unchecked 无法判断 / risk 有风险
     * @param required 要求（院校背景类、超过 90 天未核实时为 null）
     * @param actual 学生情况（院校背景类为 null）
     */
    public record Check(String item, String outcome, String required, String actual)
    {
    }

    /**
     * 档位分布提示
     *
     * @param code no_safety 没有保底 / no_match 没有主攻 / reach_heavy 冲刺过多 / mostly_blocked 多数项目暂不满足要求 / too_few 项目偏少
     * @param suggested 建议数量（按首选国家 / 地区引擎配置的推荐组合；no_safety、no_match、too_few 有值，其他为 null）
     */
    public record Hint(String code, Integer suggested)
    {
    }
}
