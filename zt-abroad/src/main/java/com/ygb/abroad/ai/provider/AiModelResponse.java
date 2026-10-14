package com.ygb.abroad.ai.provider;

/**
 * 模型响应
 *
 * @author ygb
 */
public class AiModelResponse
{
    /** 输出原文 */
    private String content;

    /** 输入 token */
    private int promptTokens;

    /** 输出 token */
    private int completionTokens;

    public AiModelResponse()
    {
    }

    public AiModelResponse(String content, int promptTokens, int completionTokens)
    {
        this.content = content;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public int getPromptTokens()
    {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens)
    {
        this.promptTokens = promptTokens;
    }

    public int getCompletionTokens()
    {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens)
    {
        this.completionTokens = completionTokens;
    }
}
