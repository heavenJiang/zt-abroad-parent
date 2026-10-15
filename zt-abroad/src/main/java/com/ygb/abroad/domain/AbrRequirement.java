package com.ygb.abroad.domain;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 项目申请要求表 abr_requirement
 * 
 * @author ygb
 */
public class AbrRequirement extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 要求ID */
    private Long requirementId;

    /** 项目ID */
    private Long programId;

    /** 申请季（如 2027-fall） */
    private String season;

    /** 按本科层次的最低均分（JSON 对象） */
    private String minGpa;

    /** 不接受的本科层次（逗号分隔） */
    private String tiersNotAccepted;

    /** 雅思总分要求 */
    private BigDecimal ieltsOverall;

    /** 雅思最低小分要求 */
    private BigDecimal ieltsMinSub;

    /** 其他语言要求（JSON 对象） */
    private String otherLanguage;

    /** GRE/GMAT 要求（字典 abr_test_requirement） */
    private String testRequirement;

    /** 标化分数要求（JSON 对象） */
    private String testDetail;

    /** 先修课要求 */
    private String prerequisites;

    /** 先修课是否硬性要求（Y是 N否） */
    private String prerequisitesRequired;

    /** 工作年限要求（年） */
    private Integer workYears;

    /** 是否接受跨专业（0不接受 1接受 2未知） */
    private String acceptCrossMajor;

    /** 是否接受语言班（0不接受 1接受 2未知） */
    private String acceptPreSessional;

    /** 官网不设均分线（Y是 N否） */
    private String gpaNoCutoff;

    /** 截止日（JSON 对象） */
    private String deadlines;

    /** 状态（0正常 1停用） */
    private String status;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    /** 字段来源（保存时一并提交，非表字段） */
    private List<AbrFieldSource> sources;

    public void setRequirementId(Long requirementId)
    {
        this.requirementId = requirementId;
    }

    public Long getRequirementId()
    {
        return requirementId;
    }

    public void setProgramId(Long programId)
    {
        this.programId = programId;
    }

    @NotNull(message = "项目不能为空")
    public Long getProgramId()
    {
        return programId;
    }

    public void setSeason(String season)
    {
        this.season = season;
    }

    @NotBlank(message = "申请季不能为空")
    @Pattern(regexp = "^20\\d{2}-(fall|spring|summer|winter)$", message = "申请季格式应为 年份-入学季，如 2027-fall")
    public String getSeason()
    {
        return season;
    }

    public void setMinGpa(String minGpa)
    {
        this.minGpa = minGpa;
    }

    public String getMinGpa()
    {
        return minGpa;
    }

    public void setTiersNotAccepted(String tiersNotAccepted)
    {
        this.tiersNotAccepted = tiersNotAccepted;
    }

    public String getTiersNotAccepted()
    {
        return tiersNotAccepted;
    }

    public void setIeltsOverall(BigDecimal ieltsOverall)
    {
        this.ieltsOverall = ieltsOverall;
    }

    @DecimalMin(value = "0", message = "雅思分数不能为负")
    @DecimalMax(value = "9", message = "雅思分数不能超过9")
    public BigDecimal getIeltsOverall()
    {
        return ieltsOverall;
    }

    public void setIeltsMinSub(BigDecimal ieltsMinSub)
    {
        this.ieltsMinSub = ieltsMinSub;
    }

    @DecimalMin(value = "0", message = "雅思分数不能为负")
    @DecimalMax(value = "9", message = "雅思分数不能超过9")
    public BigDecimal getIeltsMinSub()
    {
        return ieltsMinSub;
    }

    public void setOtherLanguage(String otherLanguage)
    {
        this.otherLanguage = otherLanguage;
    }

    public String getOtherLanguage()
    {
        return otherLanguage;
    }

    public void setTestRequirement(String testRequirement)
    {
        this.testRequirement = testRequirement;
    }

    public String getTestRequirement()
    {
        return testRequirement;
    }

    public void setTestDetail(String testDetail)
    {
        this.testDetail = testDetail;
    }

    public String getTestDetail()
    {
        return testDetail;
    }

    public void setPrerequisites(String prerequisites)
    {
        this.prerequisites = prerequisites;
    }

    @Size(max = 1000, message = "先修课要求不能超过1000个字符")
    public String getPrerequisites()
    {
        return prerequisites;
    }

    public void setPrerequisitesRequired(String prerequisitesRequired)
    {
        this.prerequisitesRequired = prerequisitesRequired;
    }

    public String getPrerequisitesRequired()
    {
        return prerequisitesRequired;
    }

    public void setWorkYears(Integer workYears)
    {
        this.workYears = workYears;
    }

    @Min(value = 0, message = "工作年限不能为负")
    public Integer getWorkYears()
    {
        return workYears;
    }

    public void setAcceptCrossMajor(String acceptCrossMajor)
    {
        this.acceptCrossMajor = acceptCrossMajor;
    }

    public String getAcceptCrossMajor()
    {
        return acceptCrossMajor;
    }

    public void setAcceptPreSessional(String acceptPreSessional)
    {
        this.acceptPreSessional = acceptPreSessional;
    }

    public String getAcceptPreSessional()
    {
        return acceptPreSessional;
    }

    public void setGpaNoCutoff(String gpaNoCutoff)
    {
        this.gpaNoCutoff = gpaNoCutoff;
    }

    public String getGpaNoCutoff()
    {
        return gpaNoCutoff;
    }

    public void setDeadlines(String deadlines)
    {
        this.deadlines = deadlines;
    }

    public String getDeadlines()
    {
        return deadlines;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setSources(List<AbrFieldSource> sources)
    {
        this.sources = sources;
    }

    public List<AbrFieldSource> getSources()
    {
        return sources;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("requirementId", getRequirementId())
            .append("programId", getProgramId())
            .append("season", getSeason())
            .append("minGpa", getMinGpa())
            .append("tiersNotAccepted", getTiersNotAccepted())
            .append("ieltsOverall", getIeltsOverall())
            .append("ieltsMinSub", getIeltsMinSub())
            .append("otherLanguage", getOtherLanguage())
            .append("testRequirement", getTestRequirement())
            .append("testDetail", getTestDetail())
            .append("prerequisites", getPrerequisites())
            .append("prerequisitesRequired", getPrerequisitesRequired())
            .append("workYears", getWorkYears())
            .append("acceptCrossMajor", getAcceptCrossMajor())
            .append("acceptPreSessional", getAcceptPreSessional())
            .append("gpaNoCutoff", getGpaNoCutoff())
            .append("deadlines", getDeadlines())
            .append("status", getStatus())
            .append("delFlag", getDelFlag())
            .append("sources", getSources())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
