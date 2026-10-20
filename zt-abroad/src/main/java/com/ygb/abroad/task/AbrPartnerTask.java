package com.ygb.abroad.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ygb.abroad.service.IAbrPartnerService;

/**
 * 定时任务：合作方分成对账（sys_job：abrPartnerTask.reconcile()，每天 03:40）
 *
 * @author ygb
 */
@Component("abrPartnerTask")
public class AbrPartnerTask
{
    @Autowired
    private IAbrPartnerService partnerService;

    /**
     * 补算新收款订单的分成，处理取消与退款
     */
    public void reconcile()
    {
        partnerService.reconcile("system");
    }
}
