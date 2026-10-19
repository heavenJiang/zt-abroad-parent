package com.ygb.abroad.domain;

import java.util.Date;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 线索跟进记录 abr_lead_follow（跟进、阶段变更、转给同事）
 *
 * @author ygb
 */
public class AbrLeadFollow extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 类型：跟进 */
    public static final String KIND_FOLLOW = "follow";

    /** 类型：阶段变更 */
    public static final String KIND_STAGE = "stage";

    /** 类型：转给同事 */
    public static final String KIND_ASSIGN = "assign";

    /** 记录ID */
    private Long followId;

    /** 线索ID */
    private Long leadId;

    /** 类型 */
    private String followKind;

    /** 跟进方式 */
    private String method;

    /** 内容 */
    @Size(max = 2000, message = "跟进内容最多 2000 个字")
    private String content;

    /** 下次联系时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date nextFollowTime;

    /** 原阶段 */
    private String stageFrom;

    /** 新阶段（记跟进时可同时改阶段） */
    private String stageTo;

    /** 原负责人 */
    private Long ownerFrom;

    /** 新负责人 */
    private Long ownerTo;

    /** 流失原因（新阶段为流失时必填） */
    private String lostReason;

    public Long getFollowId()
    {
        return followId;
    }

    public void setFollowId(Long followId)
    {
        this.followId = followId;
    }

    public Long getLeadId()
    {
        return leadId;
    }

    public void setLeadId(Long leadId)
    {
        this.leadId = leadId;
    }

    public String getFollowKind()
    {
        return followKind;
    }

    public void setFollowKind(String followKind)
    {
        this.followKind = followKind;
    }

    public String getMethod()
    {
        return method;
    }

    public void setMethod(String method)
    {
        this.method = method;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public Date getNextFollowTime()
    {
        return nextFollowTime;
    }

    public void setNextFollowTime(Date nextFollowTime)
    {
        this.nextFollowTime = nextFollowTime;
    }

    public String getStageFrom()
    {
        return stageFrom;
    }

    public void setStageFrom(String stageFrom)
    {
        this.stageFrom = stageFrom;
    }

    public String getStageTo()
    {
        return stageTo;
    }

    public void setStageTo(String stageTo)
    {
        this.stageTo = stageTo;
    }

    public Long getOwnerFrom()
    {
        return ownerFrom;
    }

    public void setOwnerFrom(Long ownerFrom)
    {
        this.ownerFrom = ownerFrom;
    }

    public Long getOwnerTo()
    {
        return ownerTo;
    }

    public void setOwnerTo(Long ownerTo)
    {
        this.ownerTo = ownerTo;
    }

    public String getLostReason()
    {
        return lostReason;
    }

    public void setLostReason(String lostReason)
    {
        this.lostReason = lostReason;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("followId", getFollowId())
            .append("leadId", getLeadId())
            .append("followKind", getFollowKind())
            .append("method", getMethod())
            .append("nextFollowTime", getNextFollowTime())
            .append("stageFrom", getStageFrom())
            .append("stageTo", getStageTo())
            .append("ownerFrom", getOwnerFrom())
            .append("ownerTo", getOwnerTo())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .toString();
    }
}
