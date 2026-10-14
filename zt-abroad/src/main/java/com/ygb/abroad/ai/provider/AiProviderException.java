package com.ygb.abroad.ai.provider;

/**
 * 模型不可用（超时、5xx、连接失败、空内容、未配置），网关据此切换备用模型
 *
 * @author ygb
 */
public class AiProviderException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public AiProviderException(String message)
    {
        super(message);
    }

    public AiProviderException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
