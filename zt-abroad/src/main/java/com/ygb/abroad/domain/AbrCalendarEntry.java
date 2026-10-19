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
 * 营销日历表 abr_calendar_entry（时间窗决定剧本生效区间，节点当天发出事件）
 * 
 * @author ygb
 */
public class AbrCalendarEntry extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 类型：时间窗 */
    public static final String TYPE_WINDOW = "window";

    /** 类型：节点 */
    public static final String TYPE_NODE = "node";

    /** 日历条目ID */
    private Long entryId;

    /** 类型（window 时间窗 / node 节点） */
    @NotBlank(message = "类型不能为空")
    private String entryType;

    /** 名称 */
    @NotBlank(message = "名称不能为空")
    @Size(max = 100, message = "名称最多 100 个字符")
    private String entryName;

    /** 剧本ID（时间窗必填） */
    private Long playbookId;

    /** 剧本编码（查询带出） */
    private String playbookCode;

    /** 剧本名称（查询带出） */
    private String playbookName;

    /** 节点编码（节点必填） */
    @Size(max = 64, message = "节点编码最多 64 个字符")
    private String nodeCode;

    /** 开始日期 */
    @NotNull(message = "开始日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date startDate;

    /** 结束日期（含当天） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date endDate;

    /** 每年重复（Y 是 N 否） */
    private String repeatYearly;

    /** 状态（0正常 1停用） */
    private String status;

    public void setEntryId(Long entryId)
    {
        this.entryId = entryId;
    }

    public Long getEntryId()
    {
        return entryId;
    }

    public void setEntryType(String entryType)
    {
        this.entryType = entryType;
    }

    public String getEntryType()
    {
        return entryType;
    }

    public void setEntryName(String entryName)
    {
        this.entryName = entryName;
    }

    public String getEntryName()
    {
        return entryName;
    }

    public void setPlaybookId(Long playbookId)
    {
        this.playbookId = playbookId;
    }

    public Long getPlaybookId()
    {
        return playbookId;
    }

    public void setPlaybookCode(String playbookCode)
    {
        this.playbookCode = playbookCode;
    }

    public String getPlaybookCode()
    {
        return playbookCode;
    }

    public void setPlaybookName(String playbookName)
    {
        this.playbookName = playbookName;
    }

    public String getPlaybookName()
    {
        return playbookName;
    }

    public void setNodeCode(String nodeCode)
    {
        this.nodeCode = nodeCode;
    }

    public String getNodeCode()
    {
        return nodeCode;
    }

    public void setStartDate(Date startDate)
    {
        this.startDate = startDate;
    }

    public Date getStartDate()
    {
        return startDate;
    }

    public void setEndDate(Date endDate)
    {
        this.endDate = endDate;
    }

    public Date getEndDate()
    {
        return endDate;
    }

    public void setRepeatYearly(String repeatYearly)
    {
        this.repeatYearly = repeatYearly;
    }

    public String getRepeatYearly()
    {
        return repeatYearly;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("entryId", getEntryId())
            .append("entryType", getEntryType())
            .append("entryName", getEntryName())
            .append("playbookId", getPlaybookId())
            .append("playbookCode", getPlaybookCode())
            .append("playbookName", getPlaybookName())
            .append("nodeCode", getNodeCode())
            .append("startDate", getStartDate())
            .append("endDate", getEndDate())
            .append("repeatYearly", getRepeatYearly())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
