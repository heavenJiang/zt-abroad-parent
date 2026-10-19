package com.ygb.abroad.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrBreakerRule;
import com.ygb.abroad.domain.AbrChannelAccount;
import com.ygb.abroad.domain.AbrEvent;
import com.ygb.abroad.mapper.AbrBreakerTripMapper;
import com.ygb.abroad.mapper.AbrChannelAccountMapper;
import com.ygb.abroad.service.IAbrChannelAccountService;
import com.ygb.abroad.service.IAbrEventService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * 渠道账号 服务层实现
 * 
 * @author ygb
 */
@Service
public class AbrChannelAccountServiceImpl implements IAbrChannelAccountService
{
    @Autowired
    private AbrChannelAccountMapper accountMapper;

    @Autowired
    private AbrBreakerTripMapper tripMapper;

    @Autowired
    private IAbrEventService eventService;

    @Override
    public List<AbrChannelAccount> selectAbrChannelAccountList(AbrChannelAccount account)
    {
        return accountMapper.selectAbrChannelAccountList(account);
    }

    @Override
    public AbrChannelAccount selectAbrChannelAccountById(Long accountId)
    {
        return accountMapper.selectAbrChannelAccountById(accountId);
    }

    @Override
    public boolean checkAccountCodeUnique(AbrChannelAccount account)
    {
        AbrChannelAccount exist = accountMapper.selectAbrChannelAccountByCode(account.getAccountCode());
        return exist == null || exist.getAccountId().equals(account.getAccountId());
    }

    @Override
    public int insertAbrChannelAccount(AbrChannelAccount account)
    {
        check(account);
        return accountMapper.insertAbrChannelAccount(account);
    }

    @Override
    public int updateAbrChannelAccount(AbrChannelAccount account)
    {
        AbrChannelAccount before = account.getAccountId() == null ? null : accountMapper.selectAbrChannelAccountById(account.getAccountId());
        if (before == null)
        {
            throw new ServiceException("账号不存在");
        }
        if (!before.getAccountCode().equals(account.getAccountCode()) && accountMapper.countAccountReferences(new Long[] { account.getAccountId() }) > 0)
        {
            throw new ServiceException("账号已有内容或发布记录，账号编码（链接渠道码）不能修改");
        }
        check(account);
        return accountMapper.updateAbrChannelAccount(account);
    }

    @Override
    public int deleteAbrChannelAccountByIds(Long[] accountIds)
    {
        if (accountMapper.countAccountReferences(accountIds) > 0)
        {
            throw new ServiceException("账号已有内容或发布记录，不能删除（可改为停用）");
        }
        return accountMapper.deleteAbrChannelAccountByIds(accountIds);
    }

    @Override
    public boolean pauseAccount(Long accountId, String reason, String operator)
    {
        AbrChannelAccount a = require(accountId);
        String text = StringUtils.trimToEmpty(reason);
        if (text.isEmpty() || text.length() > 500)
        {
            throw new ServiceException("请填写暂停原因（最多 500 个字符）");
        }
        if (accountMapper.pauseAccount(a.getAccountCode(), text, operator) == 0)
        {
            return false;
        }
        event(AbrEvent.TYPE_GROWTH_PAUSED, a.getAccountCode(), text, operator);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resumeAccount(Long accountId, String operator)
    {
        AbrChannelAccount a = require(accountId);
        if (accountMapper.resumeAccount(a.getAccountCode(), operator) == 0)
        {
            return false;
        }
        tripMapper.resolveTrips(AbrBreakerRule.SCOPE_ACCOUNT, a.getAccountCode(), operator);
        event(AbrEvent.TYPE_GROWTH_RESUMED, a.getAccountCode(), null, operator);
        return true;
    }

    private void check(AbrChannelAccount a)
    {
        requireDict("abr_platform", a.getPlatform(), "平台");
        a.setPersona(StringUtils.defaultIfBlank(a.getPersona(), "studio"));
        requireDict("abr_persona", a.getPersona(), "人设");
        a.setAuthStatus(StringUtils.defaultIfBlank(a.getAuthStatus(), "none"));
        requireDict("abr_account_auth", a.getAuthStatus(), "授权状态");
        a.setPublishMode(StringUtils.defaultIfBlank(a.getPublishMode(), AbrChannelAccount.MODE_PACKAGE));
        requireDict("abr_publish_mode", a.getPublishMode(), "发布方式");
        if (AbrChannelAccount.MODE_API.equals(a.getPublishMode()) && !"authorized".equals(a.getAuthStatus()))
        {
            throw new ServiceException("接口直发须先完成平台授权（授权状态为已授权）；未授权或平台无接口时用一键发布包");
        }
        if (StringUtils.isNotBlank(a.getStatus()) && !"0".equals(a.getStatus()) && !"1".equals(a.getStatus()))
        {
            throw new ServiceException("状态只能是 0（正常）或 1（停用）");
        }
        for (String text : new String[] { a.getAccountName(), a.getPersonaDesc(), a.getRemark() })
        {
            if (StringUtils.isNotBlank(text) && AbrAssessmentAnswers.containsTag(JsonNodeFactory.instance.stringNode(text)))
            {
                throw new ServiceException("不能包含 < 加字母的写法（类似 HTML 标签）");
            }
        }
    }

    private static void requireDict(String dictType, String value, String label)
    {
        if (!AbrPlaybookServiceImpl.dictValues(dictType).contains(value))
        {
            throw new ServiceException(label + "“" + value + "”不在字典 " + dictType + " 中");
        }
    }

    private AbrChannelAccount require(Long accountId)
    {
        AbrChannelAccount a = accountMapper.selectAbrChannelAccountById(accountId);
        if (a == null)
        {
            throw new ServiceException("账号不存在");
        }
        return a;
    }

    private void event(String type, String accountCode, String reason, String operator)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", AbrBreakerRule.SCOPE_ACCOUNT);
        payload.put("account_code", accountCode);
        payload.put("reason", reason);
        payload.put("operator", operator);
        eventService.recordSystemEvent(type, null, payload);
    }
}
