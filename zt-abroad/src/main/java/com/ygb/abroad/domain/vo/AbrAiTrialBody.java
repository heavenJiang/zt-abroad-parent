package com.ygb.abroad.domain.vo;

import java.util.List;
import java.util.Map;

/**
 * 管理端试用 Agent 的请求体
 * 
 * @author ygb
 */
public class AbrAiTrialBody
{
    /** 输入变量（填充 user.md 的 {{变量}}；mock 模型可用 _mock 模拟故障） */
    private Map<String, Object> input;

    /** 需要去标识化的姓名 */
    private List<String> knownNames;

    /** 检索到的事实（可选） */
    private List<Map<String, Object>> facts;

    public Map<String, Object> getInput()
    {
        return input;
    }

    public void setInput(Map<String, Object> input)
    {
        this.input = input;
    }

    public List<String> getKnownNames()
    {
        return knownNames;
    }

    public void setKnownNames(List<String> knownNames)
    {
        this.knownNames = knownNames;
    }

    public List<Map<String, Object>> getFacts()
    {
        return facts;
    }

    public void setFacts(List<Map<String, Object>> facts)
    {
        this.facts = facts;
    }
}
