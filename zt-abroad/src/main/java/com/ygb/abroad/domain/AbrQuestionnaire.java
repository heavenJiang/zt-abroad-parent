package com.ygb.abroad.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.annotation.Excel;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 问卷版本表 abr_questionnaire
 * 
 * @author ygb
 */
public class AbrQuestionnaire extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 草稿 */
    public static final String STATUS_DRAFT = "draft";

    /** 启用 */
    public static final String STATUS_ACTIVE = "active";

    /** 已停用 */
    public static final String STATUS_RETIRED = "retired";

    /** 问卷版本ID */
    @Excel(name = "问卷版本ID")
    private Long questionnaireId;

    /** 问卷编码 */
    @Excel(name = "问卷编码")
    private String code;

    /** 版本号 */
    @Excel(name = "版本号")
    private Integer version;

    /** 版本说明 */
    @Excel(name = "版本说明")
    private String versionLabel;

    /** 问卷标题 */
    @Excel(name = "问卷标题")
    private String title;

    /** 发布状态（字典 abr_questionnaire_status） */
    @Excel(name = "发布状态", dictType = "abr_questionnaire_status")
    private String publishStatus;

    /** 题目数 */
    @Excel(name = "题目数")
    private Integer questionCount;

    /** 问卷定义（完整 JSON） */
    private String definition;

    /** 启用人 */
    private String activatedBy;

    /** 启用时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date activatedTime;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    public void setQuestionnaireId(Long questionnaireId)
    {
        this.questionnaireId = questionnaireId;
    }

    public Long getQuestionnaireId()
    {
        return questionnaireId;
    }

    public void setCode(String code)
    {
        this.code = code;
    }

    public String getCode()
    {
        return code;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
    }

    public Integer getVersion()
    {
        return version;
    }

    public void setVersionLabel(String versionLabel)
    {
        this.versionLabel = versionLabel;
    }

    public String getVersionLabel()
    {
        return versionLabel;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getTitle()
    {
        return title;
    }

    public void setPublishStatus(String publishStatus)
    {
        this.publishStatus = publishStatus;
    }

    public String getPublishStatus()
    {
        return publishStatus;
    }

    public void setQuestionCount(Integer questionCount)
    {
        this.questionCount = questionCount;
    }

    public Integer getQuestionCount()
    {
        return questionCount;
    }

    public void setDefinition(String definition)
    {
        this.definition = definition;
    }

    public String getDefinition()
    {
        return definition;
    }

    public void setActivatedBy(String activatedBy)
    {
        this.activatedBy = activatedBy;
    }

    public String getActivatedBy()
    {
        return activatedBy;
    }

    public void setActivatedTime(Date activatedTime)
    {
        this.activatedTime = activatedTime;
    }

    public Date getActivatedTime()
    {
        return activatedTime;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("questionnaireId", getQuestionnaireId())
            .append("code", getCode())
            .append("version", getVersion())
            .append("versionLabel", getVersionLabel())
            .append("title", getTitle())
            .append("publishStatus", getPublishStatus())
            .append("questionCount", getQuestionCount())
            .append("activatedBy", getActivatedBy())
            .append("activatedTime", getActivatedTime())
            .append("delFlag", getDelFlag())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
