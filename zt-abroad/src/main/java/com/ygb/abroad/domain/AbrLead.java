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

    /** 规则等级 */
    private String ruleGrade;

    /** 得分理由（JSON 数组） */
    private String scoreReasons;

    /** 最近评分时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date scoredTime;

    /** 是否顾问修改过等级（Y / N） */
    private String gradeOverride;

    /** 顾问修改等级的原因 */
    private String overrideReason;

    /** 修改人 */
    private String overrideBy;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date overrideTime;

    public String getRuleGrade()
    {
        return ruleGrade;
    }

    public void setRuleGrade(String ruleGrade)
    {
        this.ruleGrade = ruleGrade;
    }

    public String getScoreReasons()
    {
        return scoreReasons;
    }

    public void setScoreReasons(String scoreReasons)
    {
        this.scoreReasons = scoreReasons;
    }

    public Date getScoredTime()
    {
        return scoredTime;
    }

    public void setScoredTime(Date scoredTime)
    {
        this.scoredTime = scoredTime;
    }

    public String getGradeOverride()
    {
        return gradeOverride;
    }

    public void setGradeOverride(String gradeOverride)
    {
        this.gradeOverride = gradeOverride;
    }

    public String getOverrideReason()
    {
        return overrideReason;
    }

    public void setOverrideReason(String overrideReason)
    {
        this.overrideReason = overrideReason;
    }

    public String getOverrideBy()
    {
        return overrideBy;
    }

    public void setOverrideBy(String overrideBy)
    {
        this.overrideBy = overrideBy;
    }

    public Date getOverrideTime()
    {
        return overrideTime;
    }

    public void setOverrideTime(Date overrideTime)
    {
        this.overrideTime = overrideTime;
    }

    /** 下次联系时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date nextFollowTime;

    /** 最近跟进时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastFollowTime;

    /** 进入当前阶段的时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date stageTime;

    /** 流失原因 */
    private String lostReason;

    /** 负责顾问姓名（查询带出） */
    private String ownerName;

    /** 目标国家 / 地区（取主访客档案，JSON 数组文本，查询带出） */
    private String targetCountries;

    public Date getNextFollowTime()
    {
        return nextFollowTime;
    }

    public void setNextFollowTime(Date nextFollowTime)
    {
        this.nextFollowTime = nextFollowTime;
    }

    public Date getLastFollowTime()
    {
        return lastFollowTime;
    }

    public void setLastFollowTime(Date lastFollowTime)
    {
        this.lastFollowTime = lastFollowTime;
    }

    public Date getStageTime()
    {
        return stageTime;
    }

    public void setStageTime(Date stageTime)
    {
        this.stageTime = stageTime;
    }

    public String getLostReason()
    {
        return lostReason;
    }

    public void setLostReason(String lostReason)
    {
        this.lostReason = lostReason;
    }

    public String getOwnerName()
    {
        return ownerName;
    }

    public void setOwnerName(String ownerName)
    {
        this.ownerName = ownerName;
    }

    public String getTargetCountries()
    {
        return targetCountries;
    }

    public void setTargetCountries(String targetCountries)
    {
        this.targetCountries = targetCountries;
    }

    /** 分配给当前负责人的时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date assignedTime;

    /** 分配方式（auto / manual） */
    private String assignType;

    /** 首次联系到期时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date slaDueTime;

    /** SLA 状态 */
    private String slaStatus;

    /** 分配后第一次跟进的时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date firstContactTime;

    public Date getAssignedTime()
    {
        return assignedTime;
    }

    public void setAssignedTime(Date assignedTime)
    {
        this.assignedTime = assignedTime;
    }

    public String getAssignType()
    {
        return assignType;
    }

    public void setAssignType(String assignType)
    {
        this.assignType = assignType;
    }

    public Date getSlaDueTime()
    {
        return slaDueTime;
    }

    public void setSlaDueTime(Date slaDueTime)
    {
        this.slaDueTime = slaDueTime;
    }

    public String getSlaStatus()
    {
        return slaStatus;
    }

    public void setSlaStatus(String slaStatus)
    {
        this.slaStatus = slaStatus;
    }

    public Date getFirstContactTime()
    {
        return firstContactTime;
    }

    public void setFirstContactTime(Date firstContactTime)
    {
        this.firstContactTime = firstContactTime;
    }

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
            .append("ruleGrade", getRuleGrade())
            .append("gradeOverride", getGradeOverride())
            .append("nextFollowTime", getNextFollowTime())
            .append("lostReason", getLostReason())
            .append("slaStatus", getSlaStatus())
            .append("mergedInto", getMergedInto())
            .append("delFlag", getDelFlag())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("remark", getRemark())
            .toString();
    }
}
