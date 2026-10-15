package com.ygb.abroad.domain.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 问卷 JSON 的校验结果（导入前预览）
 * 
 * @author ygb
 */
public class AbrQuestionnaireCheck
{
    /** 问卷编码 */
    private String code;

    /** 版本号 */
    private Integer version;

    /** 版本说明 */
    private String versionLabel;

    /** 问卷标题 */
    private String title;

    /** 步骤数 */
    private int stepCount;

    /** 题目数 */
    private int questionCount;

    /** 服务的学段（逗号分隔，取自 degree_levels） */
    private String degrees;

    public String getDegrees()
    {
        return degrees;
    }

    public void setDegrees(String degrees)
    {
        this.degrees = degrees;
    }

    /** 同编码同版本已存在时的发布状态（不存在为 null） */
    private String existingStatus;

    /** 当前启用的版本号（没有启用版本为 null） */
    private Integer activeVersion;

    /** 校验错误（为空表示可以导入） */
    private List<String> errors = new ArrayList<>();

    /**
     * 是否可以导入
     * 
     * @return 没有错误且版本不重复时为 true
     */
    public boolean isImportable()
    {
        return errors.isEmpty() && existingStatus == null;
    }

    public String getCode()
    {
        return code;
    }

    public void setCode(String code)
    {
        this.code = code;
    }

    public Integer getVersion()
    {
        return version;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
    }

    public String getVersionLabel()
    {
        return versionLabel;
    }

    public void setVersionLabel(String versionLabel)
    {
        this.versionLabel = versionLabel;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public int getStepCount()
    {
        return stepCount;
    }

    public void setStepCount(int stepCount)
    {
        this.stepCount = stepCount;
    }

    public int getQuestionCount()
    {
        return questionCount;
    }

    public void setQuestionCount(int questionCount)
    {
        this.questionCount = questionCount;
    }

    public String getExistingStatus()
    {
        return existingStatus;
    }

    public void setExistingStatus(String existingStatus)
    {
        this.existingStatus = existingStatus;
    }

    public Integer getActiveVersion()
    {
        return activeVersion;
    }

    public void setActiveVersion(Integer activeVersion)
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
}
