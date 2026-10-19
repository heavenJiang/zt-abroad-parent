package com.ygb.abroad.domain;

import java.util.Date;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 选题排期 abr_topic
 *
 * @author ygb
 */
public class AbrTopic extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 状态：待写稿 */
    public static final String STATUS_PLANNED = "planned";

    /** 状态：已写稿 */
    public static final String STATUS_DRAFTED = "drafted";

    /** 状态：已跳过 */
    public static final String STATUS_SKIPPED = "skipped";

    /** 状态：已取消 */
    public static final String STATUS_CANCELLED = "cancelled";

    /** 来源：员工插入 */
    public static final String SOURCE_MANUAL = "manual";

    /** 选题ID */
    private Long topicId;

    /** 发布账号ID */
    @NotNull(message = "发布账号不能为空")
    private Long accountId;

    /** 账号编码（查询带出） */
    private String accountCode;

    /** 账号名称（查询带出） */
    private String accountName;

    /** 平台（查询带出） */
    private String platform;

    /** 计划发布日期 */
    @NotNull(message = "计划日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date planDate;

    /** 当天第几条 */
    private Integer slotNo;

    /** 内容类型 */
    @NotBlank(message = "内容类型不能为空")
    private String contentKind;

    /** 选题标题 */
    @NotBlank(message = "选题标题不能为空")
    @Size(max = 40, message = "选题标题最多 40 个字")
    private String title;

    /** 切入点 */
    @Size(max = 200, message = "切入点最多 200 个字")
    private String angle;

    /** 文末工具 */
    private String hookTool;

    /** 所属剧本 */
    private String playbookCode;

    /** 引用的项目ID（JSON 数组） */
    private String programIds;

    /** 来源 */
    private String source;

    /** 状态 */
    private String status;

    /** 写成的内容ID */
    private Long contentId;

    /** AI 调用ID */
    private Long aiCallId;

    public Long getTopicId()
    {
        return topicId;
    }

    public void setTopicId(Long topicId)
    {
        this.topicId = topicId;
    }

    public Long getAccountId()
    {
        return accountId;
    }

    public void setAccountId(Long accountId)
    {
        this.accountId = accountId;
    }

    public String getAccountCode()
    {
        return accountCode;
    }

    public void setAccountCode(String accountCode)
    {
        this.accountCode = accountCode;
    }

    public String getAccountName()
    {
        return accountName;
    }

    public void setAccountName(String accountName)
    {
        this.accountName = accountName;
    }

    public String getPlatform()
    {
        return platform;
    }

    public void setPlatform(String platform)
    {
        this.platform = platform;
    }

    public Date getPlanDate()
    {
        return planDate;
    }

    public void setPlanDate(Date planDate)
    {
        this.planDate = planDate;
    }

    public Integer getSlotNo()
    {
        return slotNo;
    }

    public void setSlotNo(Integer slotNo)
    {
        this.slotNo = slotNo;
    }

    public String getContentKind()
    {
        return contentKind;
    }

    public void setContentKind(String contentKind)
    {
        this.contentKind = contentKind;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getAngle()
    {
        return angle;
    }

    public void setAngle(String angle)
    {
        this.angle = angle;
    }

    public String getHookTool()
    {
        return hookTool;
    }

    public void setHookTool(String hookTool)
    {
        this.hookTool = hookTool;
    }

    public String getPlaybookCode()
    {
        return playbookCode;
    }

    public void setPlaybookCode(String playbookCode)
    {
        this.playbookCode = playbookCode;
    }

    public String getProgramIds()
    {
        return programIds;
    }

    public void setProgramIds(String programIds)
    {
        this.programIds = programIds;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Long getContentId()
    {
        return contentId;
    }

    public void setContentId(Long contentId)
    {
        this.contentId = contentId;
    }

    public Long getAiCallId()
    {
        return aiCallId;
    }

    public void setAiCallId(Long aiCallId)
    {
        this.aiCallId = aiCallId;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("topicId", getTopicId())
            .append("accountId", getAccountId())
            .append("accountCode", getAccountCode())
            .append("accountName", getAccountName())
            .append("platform", getPlatform())
            .append("planDate", getPlanDate())
            .append("slotNo", getSlotNo())
            .append("contentKind", getContentKind())
            .append("title", getTitle())
            .append("angle", getAngle())
            .append("hookTool", getHookTool())
            .append("playbookCode", getPlaybookCode())
            .append("programIds", getProgramIds())
            .append("source", getSource())
            .append("status", getStatus())
            .append("contentId", getContentId())
            .append("aiCallId", getAiCallId())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
