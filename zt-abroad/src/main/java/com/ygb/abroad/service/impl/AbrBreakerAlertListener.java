package com.ygb.abroad.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import com.ygb.abroad.growth.BreakerTripped;
import com.ygb.abroad.mapper.AbrMessageMapper;
import com.ygb.abroad.service.IAbrLeadAssignService;

/**
 * 熔断、预算闸触发后通知有“一键暂停”权限（abroad:growthSetting:pause）的员工：站内提醒，并经企业微信应用消息推送
 *
 * @author ygb
 */
@Component
public class AbrBreakerAlertListener
{
    private static final Logger log = LoggerFactory.getLogger(AbrBreakerAlertListener.class);

    /** 收到通知的权限 */
    static final String PERM = "abroad:growthSetting:pause";

    @Autowired
    private AbrMessageMapper messageMapper;

    @Autowired
    private IAbrLeadAssignService assignService;

    /**
     * 触发记录提交后发通知（同一触发每人只提醒一次）
     *
     * @param e 事件
     */
    @TransactionalEventListener(fallbackExecution = true)
    public void onTripped(BreakerTripped e)
    {
        String kind = e.budget() ? "budget_exceeded" : "breaker_tripped";
        for (Long userId : messageMapper.selectUsersWithPerm(PERM))
        {
            try
            {
                assignService.notify(userId, kind, e.title(), e.detail(), null, kind + ":" + e.tripId());
            }
            catch (RuntimeException ex)
            {
                log.warn("熔断通知员工 {} 失败：{}", userId, ex.getMessage());
            }
        }
    }
}
