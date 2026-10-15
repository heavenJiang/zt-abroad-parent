package com.ygb.abroad.service;

import com.ygb.abroad.domain.vo.AppPolicyVo;

/**
 * 协议版本 服务层
 * 
 * @author ygb
 */
public interface IAbrPolicyService
{
    /** 隐私政策编码（与问卷 consent.policy_code 一致） */
    public static final String PRIVACY_POLICY = "abr_privacy_policy";

    /**
     * 取某协议当前启用的版本
     * 
     * @param policyCode 协议编码
     * @return 协议；没有启用版本时抛出业务异常
     */
    public AppPolicyVo selectActivePolicy(String policyCode);

    /**
     * 校验学生同意的版本是当前启用的版本
     * 
     * @param policyCode 协议编码
     * @param version 学生同意的版本
     */
    public void checkConsentVersion(String policyCode, String version);
}
