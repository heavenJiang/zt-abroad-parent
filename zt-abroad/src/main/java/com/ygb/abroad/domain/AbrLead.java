package com.ygb.abroad.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ygb.common.annotation.Sensitive;
import com.ygb.common.core.domain.BaseEntity;
import com.ygb.common.enums.DesensitizedType;

/**
 * 线索 abr_lead（每个人一条）
 *
 * @author ygb
 */
public class AbrLead extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 阶段：新线索 */
    public static final String STAGE_NEW = "new";

    /** 线索ID */
    private Long leadId;

    /** 称呼 */
    private String displayName;

    /** 手机号（列表与详情返回脱敏值） */
    @Sensitive(desensitizedType = DesensitizedType.PHONE)
    private String phone;

    /** 手机号哈希 */
    @JsonIgnore
    private String phoneHash;

    /** 微信 unionid */
    @JsonIgnore
    private String unionid;

    /** 学生账号ID */
    private Long appUserId;

    /** 主访客ID */
    private Long visitorId;

    /** 是否未成年（Y / N） */
    private String minor;

    /** 阶段 */
    private String stage;

    /** 意向度 */
    private String grade;

    /** 意向分 */
    private Integer score;

    /** 负责顾问 */
    private Long ownerUserId;

    /** 首次来源 */
    private String firstSource;

    /** 首次渠道码 */
    private String firstChannel;

    /** 首次内容编号 */
    private String firstContent;

    /** 首次触达时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date firstTouchTime;

    /** 最近来源 */
    private String lastSource;

    /** 最近渠道码 */
    private String lastChannel;

    /** 最近触达时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastTouchTime;

    /** 触达次数 */
    private Integer touchCount;

    /** 合并进的线索ID */
    private Long mergedInto;

    /** 删除标志 */
    private String delFlag;

    public Long getLeadId()
    {
        return leadId;
    }

    public void setLeadId(Long leadId)
    {
        this.leadId = leadId;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }

    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public String getPhoneHash()
    {
        return phoneHash;
    }

    public void setPhoneHash(String phoneHash)
    {
        this.phoneHash = phoneHash;
    }

    public String getUnionid()
    {
        return unionid;
    }

    public void setUnionid(String unionid)
    {
        this.unionid = unionid;
    }

    public Long getAppUserId()
    {
        return appUserId;
    }

    public void setAppUserId(Long appUserId)
    {
        this.appUserId = appUserId;
    }

    public Long getVisitorId()
    {
        return visitorId;
    }

    public void setVisitorId(Long visitorId)
    {
        this.visitorId = visitorId;
    }

    public String getMinor()
    {
        return minor;
    }

    public void setMinor(String minor)
    {
        this.minor = minor;
    }

    public String getStage()
    {
        return stage;
    }

    public void setStage(String stage)
    {
        this.stage = stage;
    }

    public String getGrade()
    {
        return grade;
    }

    public void setGrade(String grade)
    {
        this.grade = grade;
    }

    public Integer getScore()
    {
        return score;
    }

    public void setScore(Integer score)
    {
        this.score = score;
    }

    public Long getOwnerUserId()
    {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId)
    {
        this.ownerUserId = ownerUserId;
    }

    public String getFirstSource()
    {
        return firstSource;
    }

    public void setFirstSource(String firstSource)
    {
        this.firstSource = firstSource;
    }

    public String getFirstChannel()
    {
        return firstChannel;
    }

    public void setFirstChannel(String firstChannel)
    {
        this.firstChannel = firstChannel;
    }

    public String getFirstContent()
    {
        return firstContent;
    }

    public void setFirstContent(String firstContent)
    {
        this.firstContent = firstContent;
    }

    public Date getFirstTouchTime()
    {
        return firstTouchTime;
    }

    public void setFirstTouchTime(Date firstTouchTime)
    {
        this.firstTouchTime = firstTouchTime;
    }

    public String getLastSource()
    {
        return lastSource;
    }

    public void setLastSource(String lastSource)
    {
        this.lastSource = lastSource;
    }

    public String getLastChannel()
    {
        return lastChannel;
    }

    public void setLastChannel(String lastChannel)
    {
        this.lastChannel = lastChannel;
    }

    public Date getLastTouchTime()
    {
        return lastTouchTime;
    }

    public void setLastTouchTime(Date lastTouchTime)
    {
        this.lastTouchTime = lastTouchTime;
    }

    public Integer getTouchCount()
    {
        return touchCount;
    }

    public void setTouchCount(Integer touchCount)
    {
        this.touchCount = touchCount;
    }

    public Long getMergedInto()
    {
        return mergedInto;
    }

    public void setMergedInto(Long mergedInto)
    {
        this.mergedInto = mergedInto;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("leadId", getLeadId())
            .append("displayName", getDisplayName())
            .append("appUserId", getAppUserId())
            .append("visitorId", getVisitorId())
            .append("minor", getMinor())
            .append("stage", getStage())
            .append("grade", getGrade())
            .append("score", getScore())
            .append("ownerUserId", getOwnerUserId())
            .append("firstSource", getFirstSource())
            .append("firstChannel", getFirstChannel())
            .append("firstContent", getFirstContent())
            .append("firstTouchTime", getFirstTouchTime())
            .append("lastSource", getLastSource())
            .append("lastChannel", getLastChannel())
            .append("lastTouchTime", getLastTouchTime())
            .append("touchCount", getTouchCount())
            .append("mergedInto", getMergedInto())
            .append("delFlag", getDelFlag())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("remark", getRemark())
            .toString();
    }
}
