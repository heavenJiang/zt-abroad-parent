package com.ygb.abroad.mapper;

import com.ygb.abroad.domain.AbrPolicy;

/**
 * 协议版本 数据层
 * 
 * @author ygb
 */
public interface AbrPolicyMapper
{
    /**
     * 查询某协议当前启用的版本（含全文）
     * 
     * @param policyCode 协议编码
     * @return 启用的版本，没有返回 null
     */
    public AbrPolicy selectActiveAbrPolicy(String policyCode);
}
