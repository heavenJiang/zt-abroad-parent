package com.ygb.abroad.domain.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * C 端：雅思够不够结果
 * 
 * @param toolRunId 运行ID
 * @param score 学生成绩（展示用，如“雅思 6.5（小分 6.0）”）
 * @param countries 已开放国家 / 地区的结果
 * @param closedCountries 暂未开放的国家 / 地区
 * @author ygb
 */
public record AppIeltsVo(Long toolRunId, String score, List<Country> countries, List<AppQuickResultVo.CountryRef> closedCountries)
{
    /**
     * 带上运行ID
     * 
     * @param toolRunId 运行ID
     * @return 新结果
     */
    public AppIeltsVo withToolRunId(Long toolRunId)
    {
        return new AppIeltsVo(toolRunId, score, countries, closedCountries);
    }

    /**
     * 一个国家 / 地区
     * 
     * @param country 代码
     * @param label 名称
     * @param metCount 达标的项目数
     * @param preSessionalCount 读语言班可达标的项目数
     * @param notMetCount 未达标的项目数
     * @param uncheckedCount 要求未录入或未核实、无法判断的项目数
     * @param programs 项目（达标 → 语言班 → 未达标按差距从小到大 → 无法判断）
     */
    public record Country(String country, String label, int metCount, int preSessionalCount, int notMetCount, int uncheckedCount,
            List<Program> programs)
    {
    }

    /**
     * 一个项目
     * 
     * @param programId 项目ID
     * @param schoolName 院校
     * @param programName 项目
     * @param outcome met 达标 / pre_sessional 读语言班可达标 / not_met 未达标 / unchecked 无法判断
     * @param required 要求（如“雅思 7.0（小分 6.5）”；未录入或超过 90 天未核实时为 null）
     * @param totalGap 总分还差（未达标时）
     * @param subGap 小分还差（未达标时）
     * @param estimated 按同类院校一般要求估算
     * @param preSessionalUnverified 项目是否接受语言班待核实
     * @param unverified 项目信息超过 90 天未核实
     */
    public record Program(Long programId, String schoolName, String programName, String outcome, String required, BigDecimal totalGap,
            BigDecimal subGap, boolean estimated, boolean preSessionalUnverified, boolean unverified)
    {
    }
}
