package com.ygb.abroad.domain.vo;

import java.util.List;

/**
 * C 端：本校认可度结果（各国家 / 地区项目对学生本科院校的接收情况；不展示院校层次）
 * 
 * @param toolRunId 运行ID
 * @param notices 提示码（school_not_found 学校未收录，按较保守口径）
 * @param countries 已开放国家 / 地区的结果
 * @param closedCountries 暂未开放的国家 / 地区
 * @author ygb
 */
public record AppRecognitionVo(Long toolRunId, List<String> notices, List<Country> countries, List<AppQuickResultVo.CountryRef> closedCountries)
{
    /**
     * 带上运行ID
     * 
     * @param toolRunId 运行ID
     * @return 新结果
     */
    public AppRecognitionVo withToolRunId(Long toolRunId)
    {
        return new AppRecognitionVo(toolRunId, notices, countries, closedCountries);
    }

    /**
     * 一个国家 / 地区
     * 
     * @param country 代码
     * @param label 名称
     * @param acceptedCount 接收的项目数
     * @param notAcceptedCount 不接收的项目数
     * @param programs 完整列表（接收的在前，按院校名）
     */
    public record Country(String country, String label, int acceptedCount, int notAcceptedCount, List<Program> programs)
    {
    }

    /**
     * 一个项目
     * 
     * @param programId 项目ID
     * @param schoolName 院校
     * @param programName 项目
     * @param status accepted 接收 / not_accepted 不接收你所在院校背景
     * @param gpaRequired 对你院校背景的均分要求（如“≥ 85”“官网不设均分线”；未录入或超过 90 天未核实时为 null）
     * @param estimated 按同类院校一般要求估算（项目未写明）
     * @param unverified 项目信息超过 90 天未核实（不展示数值，以官网为准）
     */
    public record Program(Long programId, String schoolName, String programName, String status, String gpaRequired, boolean estimated,
            boolean unverified)
    {
    }
}
