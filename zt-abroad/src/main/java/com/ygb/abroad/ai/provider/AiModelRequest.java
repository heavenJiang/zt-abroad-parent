package com.ygb.abroad.ai.provider;

import java.util.Map;
import com.ygb.abroad.ai.AiPrompt;
import com.ygb.abroad.ai.AiProperties;

/**
 * 模型请求
 *
 * @author ygb
 */
public class AiModelRequest
{
    /** 供应商名称 */
    private String providerName;

    /** 供应商配置（含 API Key，禁止打印） */
    private AiProperties.Provider provider;

    /** 模型名 */
    private String model;

    /** 系统提示词 */
    private String systemPrompt;

    /** 用户提示词（已渲染） */
    private String userPrompt;

    /** 超时（毫秒） */
    private int timeoutMs;

    /** 输出 token 上限 */
    private int maxTokens;

    /** 采样温度 */
    private double temperature;

    /** 去标识化后的输入（mock 模型读取 _mock 与模板变量） */
    private Map<String, Object> input;

    /** 提示词（mock 模型读取 mock.json） */
    private AiPrompt prompt;

    /** 第几轮（Schema 校验失败重试为第 2 轮） */
    private int attempt;

    /** 是否主模型（false 为备用模型） */
    private boolean primary;

    public String getProviderName()
    {
        return providerName;
    }

    public void setProviderName(String providerName)
    {
        this.providerName = providerName;
    }

    public AiProperties.Provider getProvider()
    {
        return provider;
    }

    public void setProvider(AiProperties.Provider provider)
    {
        this.provider = provider;
    }

    public String getModel()
    {
        return model;
    }

    public void setModel(String model)
    {
        this.model = model;
    }

    public String getSystemPrompt()
    {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt)
    {
        this.systemPrompt = systemPrompt;
    }

    public String getUserPrompt()
    {
        return userPrompt;
    }

    public void setUserPrompt(String userPrompt)
    {
        this.userPrompt = userPrompt;
    }

    public int getTimeoutMs()
    {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs)
    {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxTokens()
    {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens)
    {
        this.maxTokens = maxTokens;
    }

    public double getTemperature()
    {
        return temperature;
    }

    public void setTemperature(double temperature)
    {
        this.temperature = temperature;
    }

    public Map<String, Object> getInput()
    {
        return input;
    }

    public void setInput(Map<String, Object> input)
    {
        this.input = input;
    }

    public AiPrompt getPrompt()
    {
        return prompt;
    }

    public void setPrompt(AiPrompt prompt)
    {
        this.prompt = prompt;
    }

    public int getAttempt()
    {
        return attempt;
    }

    public void setAttempt(int attempt)
    {
        this.attempt = attempt;
    }

    public boolean isPrimary()
    {
        return primary;
    }

    public void setPrimary(boolean primary)
    {
        this.primary = primary;
    }
}
