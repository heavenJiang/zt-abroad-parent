package com.ygb.abroad.ai;

import tools.jackson.databind.JsonNode;

/**
 * 一个 Agent 某个版本的提示词（prompts/&lt;agentCode&gt;/&lt;version&gt;/）
 *
 * @author ygb
 */
public class AiPrompt
{
    /** Agent 编码 */
    private final String agentCode;

    /** 版本（目录名，如 v1） */
    private final String version;

    /** system.md */
    private final String system;

    /** user.md（{{变量}} 模板） */
    private final String userTemplate;

    /** schema.json */
    private final JsonNode schema;

    /** mock.json（mock 模型返回的固定输出，可为空） */
    private final JsonNode mock;

    public AiPrompt(String agentCode, String version, String system, String userTemplate, JsonNode schema, JsonNode mock)
    {
        this.agentCode = agentCode;
        this.version = version;
        this.system = system;
        this.userTemplate = userTemplate;
        this.schema = schema;
        this.mock = mock;
    }

    public String getAgentCode()
    {
        return agentCode;
    }

    public String getVersion()
    {
        return version;
    }

    public String getSystem()
    {
        return system;
    }

    public String getUserTemplate()
    {
        return userTemplate;
    }

    public JsonNode getSchema()
    {
        return schema;
    }

    public JsonNode getMock()
    {
        return mock;
    }
}
