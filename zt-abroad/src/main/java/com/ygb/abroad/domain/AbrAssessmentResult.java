package com.ygb.abroad.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 测评选校结果表 abr_assessment_result
 * 
 * @author ygb
 */
public class AbrAssessmentResult extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 结果ID */
    private Long resultId;

    /** 测评ID */
    private Long assessmentId;

    /** 模式（full 完整测评 / quick 快测） */
    private String mode;

    /** 所用引擎配置版本（JSON：国家 → 版本） */
    private String engineVersions;

    /** 没有启用配置、未计算的国家 / 地区（逗号分隔） */
    private String skippedCountries;

    /** 参与计算的项目数 */
    private Integer programCount;

    /** 冲刺项目数 */
    private Integer reachCount;

    /** 主攻项目数 */
    private Integer matchCount;

    /** 保底项目数 */
    private Integer safetyCount;

    /** 补足后可申项目数 */
    private Integer needsImprovementCount;

    /** 各项目结果（JSON 数组） */
    private String programs;

    /** 推荐组合（JSON：档位 → 项目ID 数组） */
    private String portfolio;

    /** 差距反算（JSON 数组） */
    private String gaps;

    /** 提示（JSON 数组） */
    private String hints;

    /** 需要向学生展示的提示码（JSON 数组） */
    private String notices;

    /** 各国子分（JSON） */
    private String subScores;

    /** 计算时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date computedTime;

    public void setResultId(Long resultId)
    {
        this.resultId = resultId;
    }

    public Long getResultId()
    {
        return resultId;
    }

    public void setAssessmentId(Long assessmentId)
    {
        this.assessmentId = assessmentId;
    }

    public Long getAssessmentId()
    {
        return assessmentId;
    }

    public void setMode(String mode)
    {
        this.mode = mode;
    }

    public String getMode()
    {
        return mode;
    }

    public void setEngineVersions(String engineVersions)
    {
        this.engineVersions = engineVersions;
    }

    public String getEngineVersions()
    {
        return engineVersions;
    }

    public void setSkippedCountries(String skippedCountries)
    {
        this.skippedCountries = skippedCountries;
    }

    public String getSkippedCountries()
    {
        return skippedCountries;
    }

    public void setProgramCount(Integer programCount)
    {
        this.programCount = programCount;
    }

    public Integer getProgramCount()
    {
        return programCount;
    }

    public void setReachCount(Integer reachCount)
    {
        this.reachCount = reachCount;
    }

    public Integer getReachCount()
    {
        return reachCount;
    }

    public void setMatchCount(Integer matchCount)
    {
        this.matchCount = matchCount;
    }

    public Integer getMatchCount()
    {
        return matchCount;
    }

    public void setSafetyCount(Integer safetyCount)
    {
        this.safetyCount = safetyCount;
    }

    public Integer getSafetyCount()
    {
        return safetyCount;
    }

    public void setNeedsImprovementCount(Integer needsImprovementCount)
    {
        this.needsImprovementCount = needsImprovementCount;
    }

    public Integer getNeedsImprovementCount()
    {
        return needsImprovementCount;
    }

    public void setPrograms(String programs)
    {
        this.programs = programs;
    }

    public String getPrograms()
    {
        return programs;
    }

    public void setPortfolio(String portfolio)
    {
        this.portfolio = portfolio;
    }

    public String getPortfolio()
    {
        return portfolio;
    }

    public void setGaps(String gaps)
    {
        this.gaps = gaps;
    }

    public String getGaps()
    {
        return gaps;
    }

    public void setHints(String hints)
    {
        this.hints = hints;
    }

    public String getHints()
    {
        return hints;
    }

    public void setNotices(String notices)
    {
        this.notices = notices;
    }

    public String getNotices()
    {
        return notices;
    }

    public void setSubScores(String subScores)
    {
        this.subScores = subScores;
    }

    public String getSubScores()
    {
        return subScores;
    }

    public void setComputedTime(Date computedTime)
    {
        this.computedTime = computedTime;
    }

    public Date getComputedTime()
    {
        return computedTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("resultId", getResultId())
            .append("assessmentId", getAssessmentId())
            .append("mode", getMode())
            .append("engineVersions", getEngineVersions())
            .append("skippedCountries", getSkippedCountries())
            .append("programCount", getProgramCount())
            .append("reachCount", getReachCount())
            .append("matchCount", getMatchCount())
            .append("safetyCount", getSafetyCount())
            .append("needsImprovementCount", getNeedsImprovementCount())
            .append("portfolio", getPortfolio())
            .append("hints", getHints())
            .append("notices", getNotices())
            .append("computedTime", getComputedTime())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
