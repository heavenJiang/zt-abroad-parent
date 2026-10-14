package com.ygb.abroad.ai.provider;

/**
 * 模型供应商（只在网关内部使用，业务代码禁止直接调用）
 *
 * @author ygb
 */
public interface AiProvider
{
    /**
     * 对话补全
     *
     * @param request 请求
     * @return 响应（content 为模型输出原文）
     * @throws AiProviderException 超时、5xx、连接失败、空内容等，网关据此切换备用模型
     */
    public AiModelResponse chat(AiModelRequest request);
}
