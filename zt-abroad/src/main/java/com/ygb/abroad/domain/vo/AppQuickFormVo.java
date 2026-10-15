package com.ygb.abroad.domain.vo;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * C 端：30 秒快测输入页（题目取自该学段启用的问卷，与完整测评同一套选项与换算）
 * 
 * @author ygb
 */
public class AppQuickFormVo
{
    /** 问卷版本ID */
    private Long questionnaireId;

    /** 需要同意的隐私政策编码 */
    private String policyCode;

    /** 快测四题的定义（按快测顺序） */
    private List<JsonNode> questions;

    /** 国家 / 地区选项（open 为是否已开放快测） */
    private List<Country> countries;

    public Long getQuestionnaireId()
    {
        return questionnaireId;
    }

    public void setQuestionnaireId(Long questionnaireId)
    {
        this.questionnaireId = questionnaireId;
    }

    public String getPolicyCode()
    {
        return policyCode;
    }

    public void setPolicyCode(String policyCode)
    {
        this.policyCode = policyCode;
    }

    public List<JsonNode> getQuestions()
    {
        return questions;
    }

    public void setQuestions(List<JsonNode> questions)
    {
        this.questions = questions;
    }

    public List<Country> getCountries()
    {
        return countries;
    }

    public void setCountries(List<Country> countries)
    {
        this.countries = countries;
    }

    /**
     * 国家 / 地区选项
     * 
     * @param value 代码
     * @param label 名称
     * @param open 是否已开放快测（有启用的引擎配置）
     */
    public record Country(String value, String label, boolean open)
    {
    }
}
