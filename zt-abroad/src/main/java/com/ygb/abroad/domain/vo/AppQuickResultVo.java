package com.ygb.abroad.domain.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * C 端：30 秒快测结果（只含学生可见字段：档位、代表校、关键差距；不含综合分、录取概率、参考线、院校层次）
 *
 * @author ygb
 */
public class AppQuickResultVo
{
    /** 运行ID（加入选校单时带上，后端据此取档位） */
    private Long toolRunId;

    /** 需要展示的提示码（quick_academic_only 仅按院校层次和均分估算；school_not_found 学校未收录，按保守口径估算） */
    private List<String> notices;

    /** 换算后的百分制均分（展示“按均分 xx 估算”） */
    private BigDecimal gpaPercent;

    /** 已开放国家 / 地区的结果（按学生选择顺序） */
    private List<CountryResult> countries;

    /** 暂未开放快测的国家 / 地区 */
    private List<CountryRef> closedCountries;

    public Long getToolRunId()
    {
        return toolRunId;
    }

    public void setToolRunId(Long toolRunId)
    {
        this.toolRunId = toolRunId;
    }

    public List<String> getNotices()
    {
        return notices;
    }

    public void setNotices(List<String> notices)
    {
        this.notices = notices;
    }

    public BigDecimal getGpaPercent()
    {
        return gpaPercent;
    }

    public void setGpaPercent(BigDecimal gpaPercent)
    {
        this.gpaPercent = gpaPercent;
    }

    public List<CountryResult> getCountries()
    {
        return countries;
    }

    public void setCountries(List<CountryResult> countries)
    {
        this.countries = countries;
    }

    public List<CountryRef> getClosedCountries()
    {
        return closedCountries;
    }

    public void setClosedCountries(List<CountryRef> closedCountries)
    {
        this.closedCountries = closedCountries;
    }

    /**
     * 国家 / 地区
     *
     * @param country 代码
     * @param label 名称
     */
    public record CountryRef(String country, String label)
    {
    }

    /**
     * 一个国家 / 地区的快测结果
     *
     * @param country 代码
     * @param label 名称
     * @param programCount 参与计算的项目数
     * @param tiers 冲 / 稳 / 保（固定三组，按此顺序）
     * @param unratedCount 待定档（缺参考线，由顾问定档）的项目数
     * @param needsImprovementCount 补足后可申的项目数
     * @param needsImprovement 补足后可申的代表项目（含未达标项）
     * @param keyGaps 最关键的差距（按影响的项目数从多到少）
     * @param hints 提示码（mix_application 建议混申、no_safety_in_first_choice 首选国家没有保底）
     */
    public record CountryResult(String country, String label, int programCount, List<TierGroup> tiers, int unratedCount,
            int needsImprovementCount, List<ProgramRef> needsImprovement, List<KeyGap> keyGaps, List<String> hints)
    {
    }

    /**
     * 一个档位
     *
     * @param tier reach / match / safety
     * @param count 该档项目数
     * @param examples 代表项目（档内排序靠前的几个）
     */
    public record TierGroup(String tier, int count, List<ProgramRef> examples)
    {
    }

    /**
     * 代表项目
     *
     * @param programId 项目ID
     * @param schoolName 院校
     * @param programName 项目
     * @param requirementUnverified 申请要求按同难度院校的一般要求估算（项目要求待核实）
     * @param gaps 未达标项（只有补足后可申的项目有）
     */
    public record ProgramRef(Long programId, String schoolName, String programName, boolean requirementUnverified, List<GapItem> gaps)
    {
    }

    /**
     * 未达标项
     *
     * @param item gpa 均分 / tier_accepted 本科院校背景 / language_en 等
     * @param required 要求（展示用；院校背景类不展示）
     * @param actual 学生情况（展示用；院校背景类不展示）
     * @param estimated 要求是按一般要求估算的
     */
    public record GapItem(String item, String required, String actual, boolean estimated)
    {
    }

    /**
     * 关键差距
     *
     * @param item 未达标项
     * @param programCount 因此暂不能申的项目数
     * @param required 最容易达到的要求（均分：最低的一条要求；其他项为 null）
     * @param actual 学生情况（均分；其他项为 null）
     */
    public record KeyGap(String item, int programCount, String required, String actual)
    {
    }
}
