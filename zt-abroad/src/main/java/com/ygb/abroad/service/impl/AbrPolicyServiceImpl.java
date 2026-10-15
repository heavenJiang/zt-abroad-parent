package com.ygb.abroad.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrPolicy;
import com.ygb.abroad.domain.vo.AppPolicyVo;
import com.ygb.abroad.mapper.AbrPolicyMapper;
import com.ygb.abroad.service.IAbrPolicyService;
import com.ygb.common.exception.ServiceException;

/**
 * 协议版本 服务层实现
 * 
 * @author ygb
 */
@Service
public class AbrPolicyServiceImpl implements IAbrPolicyService
{
    @Autowired
    private AbrPolicyMapper policyMapper;

    @Override
    public AppPolicyVo selectActivePolicy(String policyCode)
    {
        AbrPolicy policy = policyMapper.selectActiveAbrPolicy(policyCode);
        if (policy == null)
        {
            throw new ServiceException("协议暂未发布");
        }
        AppPolicyVo vo = new AppPolicyVo();
        vo.setPolicyCode(policy.getPolicyCode());
        vo.setVersion(policy.getVersion());
        vo.setTitle(policy.getTitle());
        vo.setContent(policy.getContent());
        vo.setDraft("Y".equals(policy.getIsDraft()));
        vo.setEffectiveDate(policy.getEffectiveDate());
        return vo;
    }

    @Override
    public void checkConsentVersion(String policyCode, String version)
    {
        AbrPolicy policy = policyMapper.selectActiveAbrPolicy(policyCode);
        if (policy == null)
        {
            throw new ServiceException("隐私政策暂未发布，暂不能开始测评");
        }
        if (!policy.getVersion().equals(version == null ? null : version.trim()))
        {
            throw new ServiceException("隐私政策已更新，请重新阅读并同意");
        }
    }
}
