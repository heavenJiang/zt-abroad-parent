package com.ygb.abroad.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 线索分配设置 abr_lead_assign_setting（单行；assignGrades、slaHours 为 JSON 文本）
 *
 * @author ygb
 */
public class AbrLeadAssignSetting extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 固定为 1 */
    private Integer settingId;

    /** 是否自动分配（Y / N） */
    private String autoAssign;

    /** 是否按国家与学段匹配（Y / N） */
    private String matchCountry;

    /** 挑选方式（round_robin / load） */
    private String pickStrategy;

    /** 自动分配的意向度（JSON 数组） */
    private String assignGrades;

    /** 首次联系时限（JSON 对象：意向度 → 小时） */
    private String slaHours;

    /** 超时后再过几小时升级 */
    private Integer escalateHours;

    /** 升级给谁 */
    private Long escalateUserId;

    /** 升级对象姓名（查询带出） */
    private String escalateUserName;

    public Integer getSettingId()
    {
        return settingId;
    }

    public void setSettingId(Integer settingId)
    {
        this.settingId = settingId;
    }

    public String getAutoAssign()
    {
        return autoAssign;
    }

    public void setAutoAssign(String autoAssign)
    {
        this.autoAssign = autoAssign;
    }

    public String getMatchCountry()
    {
        return matchCountry;
    }

    public void setMatchCountry(String matchCountry)
    {
        this.matchCountry = matchCountry;
    }

    public String getPickStrategy()
    {
        return pickStrategy;
    }

    public void setPickStrategy(String pickStrategy)
    {
        this.pickStrategy = pickStrategy;
    }

    public String getAssignGrades()
    {
        return assignGrades;
    }

    public void setAssignGrades(String assignGrades)
    {
        this.assignGrades = assignGrades;
    }

    public String getSlaHours()
    {
        return slaHours;
    }

    public void setSlaHours(String slaHours)
    {
        this.slaHours = slaHours;
    }

    public Integer getEscalateHours()
    {
        return escalateHours;
    }

    public void setEscalateHours(Integer escalateHours)
    {
        this.escalateHours = escalateHours;
    }

    public Long getEscalateUserId()
    {
        return escalateUserId;
    }

    public void setEscalateUserId(Long escalateUserId)
    {
        this.escalateUserId = escalateUserId;
    }

    public String getEscalateUserName()
    {
        return escalateUserName;
    }

    public void setEscalateUserName(String escalateUserName)
    {
        this.escalateUserName = escalateUserName;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("settingId", getSettingId())
            .append("autoAssign", getAutoAssign())
            .append("matchCountry", getMatchCountry())
            .append("pickStrategy", getPickStrategy())
            .append("assignGrades", getAssignGrades())
            .append("slaHours", getSlaHours())
            .append("escalateHours", getEscalateHours())
            .append("escalateUserId", getEscalateUserId())
            .append("escalateUserName", getEscalateUserName())
            .toString();
    }
}
