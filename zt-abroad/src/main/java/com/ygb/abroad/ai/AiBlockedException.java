package com.ygb.abroad.ai;

/**
 * 调用被放行检查拦下（暂停、停用、超出 AI 预算），由网关转为 blocked 结果并写调用日志
 *
 * @author ygb
 */
public class AiBlockedException extends AiException
{
    private static final long serialVersionUID = 1L;

    public AiBlockedException(String message)
    {
        super(message);
    }
}
