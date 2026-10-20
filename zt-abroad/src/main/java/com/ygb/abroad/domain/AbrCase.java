package com.ygb.abroad.domain;

import java.math.BigDecimal;
import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 录取案例与 offer abr_case
 *
 * @author ygb
 */
public class AbrCase extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 案例ID */
    private Long caseId;

    /** 关联线索 */
    private Long leadId;

    /** 回填的学生账号 */
    private Long appUserId;

    /** 线索称呼 */
    private String leadName;

    /** 申请季 */
    private String season;

    /** 国家 / 地区 */
    private String country;

    /** 学段 */
    private String degree;

    /** 院校库项目 */
    private Long programId;

    /** 院校名称 */
    private String schoolName;

    /** 项目名称 */
    private String programName;

    /** 结果 */
    private String result;

    /** 奖学金 */
    private String scholarship;

    /** 本科院校层次 */
    private String undergradTier;

    /** 本科专业方向 */
    private String majorDirection;

    /** 均分 */
    private BigDecimal gpa;

    /** 雅思总分 */
    private BigDecimal ielts;

    /** 其他语言与标化 */
    private String testScores;

    /** 经历概要 */
    private String experience;

    /** 核验等级 */
    private String verifyLevel;

    /** 来源 */
    private String source;

    /** 算法使用授权 */
    private String algoConsent;

    /** 宣传授权 */
    private String publicityConsent;

    /** 最近一次授权时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date consentTime;

    /** 撤回授权时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date revokeTime;

    /** offer 截图（私有路径） */
    @JsonIgnore
    private String offerFile;

    /** 状态 */
    private String status;

    /** 是否已上传 offer 截图 */
    public boolean getHasOfferFile()
    {
        return offerFile != null && !offerFile.isEmpty();
    }

    public Long getCaseId()
    {
        return caseId;
    }

    public void setCaseId(Long caseId)
    {
        this.caseId = caseId;
    }

    public Long getLeadId()
    {
        return leadId;
    }

    public void setLeadId(Long leadId)
    {
        this.leadId = leadId;
    }

    public Long getAppUserId()
    {
        return appUserId;
    }

    public void setAppUserId(Long appUserId)
    {
        this.appUserId = appUserId;
    }

    public String getLeadName()
    {
        return leadName;
    }

    public void setLeadName(String leadName)
    {
        this.leadName = leadName;
    }

    public String getSeason()
    {
        return season;
    }

    public void setSeason(String season)
    {
        this.season = season;
    }

    public String getCountry()
    {
        return country;
    }

    public void setCountry(String country)
    {
        this.country = country;
    }

    public String getDegree()
    {
        return degree;
    }

    public void setDegree(String degree)
    {
        this.degree = degree;
    }

    public Long getProgramId()
    {
        return programId;
    }

    public void setProgramId(Long programId)
    {
        this.programId = programId;
    }

    public String getSchoolName()
    {
        return schoolName;
    }

    public void setSchoolName(String schoolName)
    {
        this.schoolName = schoolName;
    }

    public String getProgramName()
    {
        return programName;
    }

    public void setProgramName(String programName)
    {
        this.programName = programName;
    }

    public String getResult()
    {
        return result;
    }

    public void setResult(String result)
    {
        this.result = result;
    }

    public String getScholarship()
    {
        return scholarship;
    }

    public void setScholarship(String scholarship)
    {
        this.scholarship = scholarship;
    }

    public String getUndergradTier()
    {
        return undergradTier;
    }

    public void setUndergradTier(String undergradTier)
    {
        this.undergradTier = undergradTier;
    }

    public String getMajorDirection()
    {
        return majorDirection;
    }

    public void setMajorDirection(String majorDirection)
    {
        this.majorDirection = majorDirection;
    }

    public BigDecimal getGpa()
    {
        return gpa;
    }

    public void setGpa(BigDecimal gpa)
    {
        this.gpa = gpa;
    }

    public BigDecimal getIelts()
    {
        return ielts;
    }

    public void setIelts(BigDecimal ielts)
    {
        this.ielts = ielts;
    }

    public String getTestScores()
    {
        return testScores;
    }

    public void setTestScores(String testScores)
    {
        this.testScores = testScores;
    }

    public String getExperience()
    {
        return experience;
    }

    public void setExperience(String experience)
    {
        this.experience = experience;
    }

    public String getVerifyLevel()
    {
        return verifyLevel;
    }

    public void setVerifyLevel(String verifyLevel)
    {
        this.verifyLevel = verifyLevel;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getAlgoConsent()
    {
        return algoConsent;
    }

    public void setAlgoConsent(String algoConsent)
    {
        this.algoConsent = algoConsent;
    }

    public String getPublicityConsent()
    {
        return publicityConsent;
    }

    public void setPublicityConsent(String publicityConsent)
    {
        this.publicityConsent = publicityConsent;
    }

    public Date getConsentTime()
    {
        return consentTime;
    }

    public void setConsentTime(Date consentTime)
    {
        this.consentTime = consentTime;
    }

    public Date getRevokeTime()
    {
        return revokeTime;
    }

    public void setRevokeTime(Date revokeTime)
    {
        this.revokeTime = revokeTime;
    }

    @JsonIgnore
    public String getOfferFile()
    {
        return offerFile;
    }

    public void setOfferFile(String offerFile)
    {
        this.offerFile = offerFile;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("caseId", getCaseId())
            .append("leadId", getLeadId())
            .append("appUserId", getAppUserId())
            .append("leadName", getLeadName())
            .append("season", getSeason())
            .append("country", getCountry())
            .append("degree", getDegree())
            .append("programId", getProgramId())
            .append("schoolName", getSchoolName())
            .append("programName", getProgramName())
            .append("result", getResult())
            .append("scholarship", getScholarship())
            .append("undergradTier", getUndergradTier())
            .append("majorDirection", getMajorDirection())
            .append("gpa", getGpa())
            .append("ielts", getIelts())
            .append("testScores", getTestScores())
            .append("experience", getExperience())
            .append("verifyLevel", getVerifyLevel())
            .append("source", getSource())
            .append("algoConsent", getAlgoConsent())
            .append("publicityConsent", getPublicityConsent())
            .append("consentTime", getConsentTime())
            .append("revokeTime", getRevokeTime())
            .append("status", getStatus())
            .toString();
    }
}
