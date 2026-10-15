package com.ygb.abroad.domain.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 引擎配置 JSON 的校验结果（导入前预览）
 * 
 * @author ygb
 */
public class AbrEngineConfigCheck
{
    /** 配置编码 */
    private String configCode;

    /** 国家 / 地区 */
    private String country;

    /** 学位 */
    private String degree;

    /** 版本 */
    private String version;

    /** 文件状态（draft / confirmed） */
    private String fileStatus;

    /** 同编码同版本已存在时的发布状态（不存在为 null） */
    private String existingStatus;

    /** 同国家 × 学位当前启用的版本（没有为 null） */
    private String activeVersion;

    /** 校验错误（为空表示可以导入） */
    private List<String> errors = new ArrayList<>();

    /** 警告（不阻止导入，如确认后仍有“AI 草拟”说明） */
    private List<String> warnings = new ArrayList<>();

    /**
     * 是否可以导入
     * 
     * @return 没有错误且版本不重复时为 true
     */
    public boolean isImportable()
    {
        return errors.isEmpty() && existingStatus == null;
    }

    /**
     * 导入后是否可以启用
     * 
     * @return 文件状态为 confirmed 时为 true
     */
    public boolean isActivatable()
    {
        return "confirmed".equals(fileStatus);
    }

    public String getConfigCode()
    {
        return configCode;
    }

    public void setConfigCode(String configCode)
    {
        this.configCode = configCode;
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

    public String getVersion()
    {
        return version;
    }

    public void setVersion(String version)
    {
        this.version = version;
    }

    public String getFileStatus()
    {
        return fileStatus;
    }

    public void setFileStatus(String fileStatus)
    {
        this.fileStatus = fileStatus;
    }

    public String getExistingStatus()
    {
        return existingStatus;
    }

    public void setExistingStatus(String existingStatus)
    {
        this.existingStatus = existingStatus;
    }

    public String getActiveVersion()
    {
        return activeVersion;
    }

    public void setActiveVersion(String activeVersion)
    {
        this.activeVersion = activeVersion;
    }

    public List<String> getErrors()
    {
        return errors;
    }

    public void setErrors(List<String> errors)
    {
        this.errors = errors;
    }

    public List<String> getWarnings()
    {
        return warnings;
    }

    public void setWarnings(List<String> warnings)
    {
        this.warnings = warnings;
    }
}
