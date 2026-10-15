package com.ygb.abroad.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.annotation.Excel;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 选校引擎配置版本表 abr_engine_config
 * 
 * @author ygb
 */
public class AbrEngineConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 草稿 */
    public static final String STATUS_DRAFT = "draft";

    /** 启用 */
    public static final String STATUS_ACTIVE = "active";

    /** 已停用 */
    public static final String STATUS_RETIRED = "retired";

    /** 文件状态：顾问已确认（只有已确认的配置可启用） */
    public static final String FILE_CONFIRMED = "confirmed";

    /** 配置ID */
    @Excel(name = "配置ID")
    private Long configId;

    /** 配置编码（如 UK_MASTER） */
    @Excel(name = "配置编码")
    private String configCode;

    /** 国家 / 地区（字典 abr_country） */
    @Excel(name = "国家地区", dictType = "abr_country")
    private String country;

    /** 学位（字典 abr_degree） */
    @Excel(name = "学位", dictType = "abr_degree")
    private String degree;

    /** 版本 */
    @Excel(name = "版本")
    private String version;

    /** 文件状态（draft 草案 / confirmed 顾问已确认） */
    @Excel(name = "文件状态")
    private String fileStatus;

    /** 发布状态（字典 abr_engine_status） */
    @Excel(name = "发布状态", dictType = "abr_engine_status")
    private String publishStatus;

    /** 引擎配置（完整 JSON） */
    private String config;

    /** 导入时的警告（JSON 数组） */
    private String warnings;

    /** 启用人 */
    private String activatedBy;

    /** 启用时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date activatedTime;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    public void setConfigId(Long configId)
    {
        this.configId = configId;
    }

    public Long getConfigId()
    {
        return configId;
    }

    public void setConfigCode(String configCode)
    {
        this.configCode = configCode;
    }

    public String getConfigCode()
    {
        return configCode;
    }

    public void setCountry(String country)
    {
        this.country = country;
    }

    public String getCountry()
    {
        return country;
    }

    public void setDegree(String degree)
    {
        this.degree = degree;
    }

    public String getDegree()
    {
        return degree;
    }

    public void setVersion(String version)
    {
        this.version = version;
    }

    public String getVersion()
    {
        return version;
    }

    public void setFileStatus(String fileStatus)
    {
        this.fileStatus = fileStatus;
    }

    public String getFileStatus()
    {
        return fileStatus;
    }

    public void setPublishStatus(String publishStatus)
    {
        this.publishStatus = publishStatus;
    }

    public String getPublishStatus()
    {
        return publishStatus;
    }

    public void setConfig(String config)
    {
        this.config = config;
    }

    public String getConfig()
    {
        return config;
    }

    public void setWarnings(String warnings)
    {
        this.warnings = warnings;
    }

    public String getWarnings()
    {
        return warnings;
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
            .append("configId", getConfigId())
            .append("configCode", getConfigCode())
            .append("country", getCountry())
            .append("degree", getDegree())
            .append("version", getVersion())
            .append("fileStatus", getFileStatus())
            .append("publishStatus", getPublishStatus())
            .append("warnings", getWarnings())
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
