package com.ygb.abroad.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 测评表 abr_assessment
 * 
 * @author ygb
 */
public class AbrAssessment extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 答题中 */
    public static final String STATUS_IN_PROGRESS = "in_progress";

    /** 已终止（如未满 14 周岁） */
    public static final String STATUS_TERMINATED = "terminated";

    /** 已提交 */
    public static final String STATUS_SUBMITTED = "submitted";

    /** 学段（字典 abr_degree，C 端首屏选择） */
    private String degree;

    public String getDegree()
    {
        return degree;
    }

    public void setDegree(String degree)
    {
        this.degree = degree;
    }

    /** 测评ID */
    private Long assessmentId;

    /** 访客ID */
    private Long visitorId;

    /** 问卷版本ID */
    private Long questionnaireId;

    /** 问卷编码 */
    private String questionnaireCode;

    /** 问卷版本号 */
    private Integer questionnaireVersion;

    /** 前端生成的请求ID（幂等创建） */
    private String requestId;

    /** 渠道码 */
    private String channelCode;

    /** 答题端（h5 / mp_weixin） */
    private String clientPlatform;

    /** 同意的隐私政策编码 */
    private String policyCode;

    /** 同意的隐私政策版本 */
    private String policyVersion;

    /** 同意时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date consentTime;

    /** 测评状态（字典 abr_assessment_status） */
    private String assessmentStatus;

    /** 答案（JSON：题目 id → 答案） */
    private String answers;

    /** 流程标志（JSON，来自问卷 on_answer.flags） */
    private String flags;

    /** 是否未成年（Y是 N否） */
    private String isMinor;

    /** 断点：最近保存时所在的屏 id */
    private String currentScreen;

    /** 提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date submittedTime;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    /** 已答题数（列表用，非表字段） */
    private Integer answerCount;

    /** 本科院校层次（来自背景档案，列表用） */
    private String undergradTier;

    /** 百分制均分（来自背景档案，列表用） */
    private BigDecimal gpaPercent;

    /** 雅思等效总分（来自背景档案，列表用） */
    private BigDecimal ieltsEquivTotal;

    /** 目标专业大类（来自背景档案，列表用） */
    private String targetFieldGroup;

    /** 归一化时间（来自背景档案，列表用） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date normalizedTime;

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

    public void setQuestionnaireCode(String questionnaireCode)
    {
        this.questionnaireCode = questionnaireCode;
    }

    public String getQuestionnaireCode()
    {
        return questionnaireCode;
    }

    public void setQuestionnaireVersion(Integer questionnaireVersion)
    {
        this.questionnaireVersion = questionnaireVersion;
    }

    public Integer getQuestionnaireVersion()
    {
        return questionnaireVersion;
    }

    public void setRequestId(String requestId)
    {
        this.requestId = requestId;
    }

    public String getRequestId()
    {
        return requestId;
    }

    public void setChannelCode(String channelCode)
    {
        this.channelCode = channelCode;
    }

    public String getChannelCode()
    {
        return channelCode;
    }

    public void setClientPlatform(String clientPlatform)
    {
        this.clientPlatform = clientPlatform;
    }

    public String getClientPlatform()
    {
        return clientPlatform;
    }

    public void setPolicyCode(String policyCode)
    {
        this.policyCode = policyCode;
    }

    public String getPolicyCode()
    {
        return policyCode;
    }

    public void setPolicyVersion(String policyVersion)
    {
        this.policyVersion = policyVersion;
    }

    public String getPolicyVersion()
    {
        return policyVersion;
    }

    public void setConsentTime(Date consentTime)
    {
        this.consentTime = consentTime;
    }

    public Date getConsentTime()
    {
        return consentTime;
    }

    public void setAssessmentStatus(String assessmentStatus)
    {
        this.assessmentStatus = assessmentStatus;
    }

    public String getAssessmentStatus()
    {
        return assessmentStatus;
    }

    public void setAnswers(String answers)
    {
        this.answers = answers;
    }

    public String getAnswers()
    {
        return answers;
    }

    public void setFlags(String flags)
    {
        this.flags = flags;
    }

    public String getFlags()
    {
        return flags;
    }

    public void setIsMinor(String isMinor)
    {
        this.isMinor = isMinor;
    }

    public String getIsMinor()
    {
        return isMinor;
    }

    public void setCurrentScreen(String currentScreen)
    {
        this.currentScreen = currentScreen;
    }

    public String getCurrentScreen()
    {
        return currentScreen;
    }

    public void setSubmittedTime(Date submittedTime)
    {
        this.submittedTime = submittedTime;
    }

    public Date getSubmittedTime()
    {
        return submittedTime;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setAnswerCount(Integer answerCount)
    {
        this.answerCount = answerCount;
    }

    public Integer getAnswerCount()
    {
        return answerCount;
    }

    public void setUndergradTier(String undergradTier)
    {
        this.undergradTier = undergradTier;
    }

    public String getUndergradTier()
    {
        return undergradTier;
    }

    public void setGpaPercent(BigDecimal gpaPercent)
    {
        this.gpaPercent = gpaPercent;
    }

    public BigDecimal getGpaPercent()
    {
        return gpaPercent;
    }

    public void setIeltsEquivTotal(BigDecimal ieltsEquivTotal)
    {
        this.ieltsEquivTotal = ieltsEquivTotal;
    }

    public BigDecimal getIeltsEquivTotal()
    {
        return ieltsEquivTotal;
    }

    public void setTargetFieldGroup(String targetFieldGroup)
    {
        this.targetFieldGroup = targetFieldGroup;
    }

    public String getTargetFieldGroup()
    {
        return targetFieldGroup;
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
            .append("assessmentId", getAssessmentId())
            .append("visitorId", getVisitorId())
            .append("questionnaireId", getQuestionnaireId())
            .append("questionnaireCode", getQuestionnaireCode())
            .append("questionnaireVersion", getQuestionnaireVersion())
            .append("requestId", getRequestId())
            .append("channelCode", getChannelCode())
            .append("clientPlatform", getClientPlatform())
            .append("policyCode", getPolicyCode())
            .append("policyVersion", getPolicyVersion())
            .append("consentTime", getConsentTime())
            .append("assessmentStatus", getAssessmentStatus())
            .append("answers", getAnswers())
            .append("flags", getFlags())
            .append("isMinor", getIsMinor())
            .append("currentScreen", getCurrentScreen())
            .append("submittedTime", getSubmittedTime())
            .append("delFlag", getDelFlag())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
