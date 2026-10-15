package com.ygb.abroad.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 测评解读表 abr_report_explanation
 * 
 * @author ygb
 */
public class AbrReportExplanation extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 状态：生成中 */
    public static final String STATUS_RUNNING = "running";

    /** 状态：已完成 */
    public static final String STATUS_DONE = "done";

    /** 状态：失败（用模板文字） */
    public static final String STATUS_FAILED = "failed";

    /** 解读ID */
    private Long explanationId;

    /** 测评ID */
    private Long assessmentId;

    /** 状态（running 生成中 / done 已完成 / failed 失败用模板） */
    private String explainStatus;

    /** 认领令牌 */
    private String claimToken;

    /** 通过校验的 AI 文字（JSON） */
    private String aiOutput;

    /** AI 网关结果状态 */
    private String aiStatus;

    /** AI 调用日志ID */
    private Long callId;

    /** 失败原因 */
    private String errorMsg;

    /** 是否对应当前的选校结果（查询时计算，不入库） */
    private Boolean current;

    public Long getExplanationId()
    {
        return explanationId;
    }

    public void setExplanationId(Long explanationId)
    {
        this.explanationId = explanationId;
    }

    public Long getAssessmentId()
    {
        return assessmentId;
    }

    public void setAssessmentId(Long assessmentId)
    {
        this.assessmentId = assessmentId;
    }

    public String getExplainStatus()
    {
        return explainStatus;
    }

    public void setExplainStatus(String explainStatus)
    {
        this.explainStatus = explainStatus;
    }

    public String getClaimToken()
    {
        return claimToken;
    }

    public void setClaimToken(String claimToken)
    {
        this.claimToken = claimToken;
    }

    public String getAiOutput()
    {
        return aiOutput;
    }

    public void setAiOutput(String aiOutput)
    {
        this.aiOutput = aiOutput;
    }

    public String getAiStatus()
    {
        return aiStatus;
    }

    public void setAiStatus(String aiStatus)
    {
        this.aiStatus = aiStatus;
    }

    public Long getCallId()
    {
        return callId;
    }

    public void setCallId(Long callId)
    {
        this.callId = callId;
    }

    public String getErrorMsg()
    {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg)
    {
        this.errorMsg = errorMsg;
    }

    public Boolean getCurrent()
    {
        return current;
    }

    public void setCurrent(Boolean current)
    {
        this.current = current;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("explanationId", getExplanationId())
            .append("assessmentId", getAssessmentId())
            .append("explainStatus", getExplainStatus())
            .append("claimToken", getClaimToken())
            .append("aiOutput", getAiOutput())
            .append("aiStatus", getAiStatus())
            .append("callId", getCallId())
            .append("errorMsg", getErrorMsg())
            .append("current", getCurrent())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
