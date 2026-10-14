package com.ygb.abroad.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 背景档案表 abr_profile
 * 
 * @author ygb
 */
public class AbrProfile extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 档案ID */
    private Long profileId;

    /** 测评ID */
    private Long assessmentId;

    /** 访客ID */
    private Long visitorId;

    /** 问卷版本ID */
    private Long questionnaireId;

    /** 本科院校层次（字典 abr_undergrad_tier） */
    private String undergradTier;

    /** 国内高校ID */
    private Long undergradUniversityId;

    /** 百分制均分 */
    private BigDecimal gpaPercent;

    /** 专业排名 */
    private String classRank;

    /** 专业相关度 */
    private String majorRelevance;

    /** 英语考试情况 */
    private String englishStatus;

    /** 雅思等效总分 */
    private BigDecimal ieltsEquivTotal;

    /** 雅思等效最低小分 */
    private BigDecimal ieltsEquivMinSub;

    /** 标化类型 */
    private String stdTestType;

    /** 标化总分 */
    private Integer stdTestTotal;

    /** 目标学位 */
    private String targetDegree;

    /** 目标专业方向 */
    private String targetField;

    /** 目标专业大类 */
    private String targetFieldGroup;

    /** 目标国家 / 地区（逗号分隔） */
    private String targetCountries;

    /** 距入学月数 */
    private Integer monthsToIntake;

    /** 完整背景档案（JSON） */
    private String profile;

    /** 提示码（JSON 数组） */
    private String warnings;

    /** 归一化时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date normalizedTime;

    public void setProfileId(Long profileId)
    {
        this.profileId = profileId;
    }

    public Long getProfileId()
    {
        return profileId;
    }

    public void setAssessmentId(Long assessmentId)
    {
        this.assessmentId = assessmentId;
    }

    public Long getAssessmentId()
    {
        return assessmentId;
    }

    public void setVisitorId(Long visitorId)
    {
        this.visitorId = visitorId;
    }

    public Long getVisitorId()
    {
        return visitorId;
    }

    public void setQuestionnaireId(Long questionnaireId)
    {
        this.questionnaireId = questionnaireId;
    }

    public Long getQuestionnaireId()
    {
        return questionnaireId;
    }

    public void setUndergradTier(String undergradTier)
    {
        this.undergradTier = undergradTier;
    }

    public String getUndergradTier()
    {
        return undergradTier;
    }

    public void setUndergradUniversityId(Long undergradUniversityId)
    {
        this.undergradUniversityId = undergradUniversityId;
    }

    public Long getUndergradUniversityId()
    {
        return undergradUniversityId;
    }

    public void setGpaPercent(BigDecimal gpaPercent)
    {
        this.gpaPercent = gpaPercent;
    }

    public BigDecimal getGpaPercent()
    {
        return gpaPercent;
    }

    public void setClassRank(String classRank)
    {
        this.classRank = classRank;
    }

    public String getClassRank()
    {
        return classRank;
    }

    public void setMajorRelevance(String majorRelevance)
    {
        this.majorRelevance = majorRelevance;
    }

    public String getMajorRelevance()
    {
        return majorRelevance;
    }

    public void setEnglishStatus(String englishStatus)
    {
        this.englishStatus = englishStatus;
    }

    public String getEnglishStatus()
    {
        return englishStatus;
    }

    public void setIeltsEquivTotal(BigDecimal ieltsEquivTotal)
    {
        this.ieltsEquivTotal = ieltsEquivTotal;
    }

    public BigDecimal getIeltsEquivTotal()
    {
        return ieltsEquivTotal;
    }

    public void setIeltsEquivMinSub(BigDecimal ieltsEquivMinSub)
    {
        this.ieltsEquivMinSub = ieltsEquivMinSub;
    }

    public BigDecimal getIeltsEquivMinSub()
    {
        return ieltsEquivMinSub;
    }

    public void setStdTestType(String stdTestType)
    {
        this.stdTestType = stdTestType;
    }

    public String getStdTestType()
    {
        return stdTestType;
    }

    public void setStdTestTotal(Integer stdTestTotal)
    {
        this.stdTestTotal = stdTestTotal;
    }

    public Integer getStdTestTotal()
    {
        return stdTestTotal;
    }

    public void setTargetDegree(String targetDegree)
    {
        this.targetDegree = targetDegree;
    }

    public String getTargetDegree()
    {
        return targetDegree;
    }

    public void setTargetField(String targetField)
    {
        this.targetField = targetField;
    }

    public String getTargetField()
    {
        return targetField;
    }

    public void setTargetFieldGroup(String targetFieldGroup)
    {
        this.targetFieldGroup = targetFieldGroup;
    }

    public String getTargetFieldGroup()
    {
        return targetFieldGroup;
    }

    public void setTargetCountries(String targetCountries)
    {
        this.targetCountries = targetCountries;
    }

    public String getTargetCountries()
    {
        return targetCountries;
    }

    public void setMonthsToIntake(Integer monthsToIntake)
    {
        this.monthsToIntake = monthsToIntake;
    }

    public Integer getMonthsToIntake()
    {
        return monthsToIntake;
    }

    public void setProfile(String profile)
    {
        this.profile = profile;
    }

    public String getProfile()
    {
        return profile;
    }

    public void setWarnings(String warnings)
    {
        this.warnings = warnings;
    }

    public String getWarnings()
    {
        return warnings;
    }

    public void setNormalizedTime(Date normalizedTime)
    {
        this.normalizedTime = normalizedTime;
    }

    public Date getNormalizedTime()
    {
        return normalizedTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("profileId", getProfileId())
            .append("assessmentId", getAssessmentId())
            .append("visitorId", getVisitorId())
            .append("questionnaireId", getQuestionnaireId())
            .append("undergradTier", getUndergradTier())
            .append("undergradUniversityId", getUndergradUniversityId())
            .append("gpaPercent", getGpaPercent())
            .append("classRank", getClassRank())
            .append("majorRelevance", getMajorRelevance())
            .append("englishStatus", getEnglishStatus())
            .append("ieltsEquivTotal", getIeltsEquivTotal())
            .append("ieltsEquivMinSub", getIeltsEquivMinSub())
            .append("stdTestType", getStdTestType())
            .append("stdTestTotal", getStdTestTotal())
            .append("targetDegree", getTargetDegree())
            .append("targetField", getTargetField())
            .append("targetFieldGroup", getTargetFieldGroup())
            .append("targetCountries", getTargetCountries())
            .append("monthsToIntake", getMonthsToIntake())
            .append("warnings", getWarnings())
            .append("normalizedTime", getNormalizedTime())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
