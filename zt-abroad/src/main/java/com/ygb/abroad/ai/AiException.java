package com.ygb.abroad.ai;

/**
 * AI 网关异常（Agent 不可用、提示词缺失、主备模型均不可用等），由网关转为 error 结果并写调用日志
 *
 * @author ygb
 */
public class AiException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public AiException(String message)
    {
        super(message);
    }

    public AiException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
