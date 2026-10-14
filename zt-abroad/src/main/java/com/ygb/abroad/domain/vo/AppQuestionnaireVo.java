package com.ygb.abroad.domain.vo;

import tools.jackson.databind.JsonNode;

/**
 * C 端：问卷定义
 * 
 * @author ygb
 */
public class AppQuestionnaireVo
{
    /** 问卷版本ID */
    private Long questionnaireId;

    /** 问卷编码 */
    private String code;

    /** 版本号 */
    private Integer version;

    /** 标题 */
    private String title;

    /** 问卷定义 */
    private JsonNode definition;

    public Long getQuestionnaireId()
    {
        return questionnaireId;
    }

    public void setQuestionnaireId(Long questionnaireId)
    {
        this.questionnaireId = questionnaireId;
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

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public JsonNode getDefinition()
    {
        return definition;
    }

    public void setDefinition(JsonNode definition)
    {
        this.definition = definition;
    }
}
