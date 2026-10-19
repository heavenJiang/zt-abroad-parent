package com.ygb.web.controller.app;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrGrowthTask;
import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.service.IAbrEventService;
import com.ygb.abroad.service.IAbrGrowthGuardService;
import com.ygb.abroad.service.IAbrVisitorService;
import com.ygb.common.annotation.Anonymous;
import com.ygb.common.annotation.RateLimiter;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.enums.LimitType;

/**
 * C 端-提醒与退订设置（营销消息可随时退订，立即生效；本人身份只取访客令牌，M4-04 登录后按学生账号）
 * 
 * @author ygb
 */
@Anonymous
@RestController
@RequestMapping("/app/subscription")
public class AppSubscriptionController extends BaseController
{
    @Autowired
    private IAbrVisitorService visitorService;

    @Autowired
    private IAbrGrowthGuardService guardService;

    @Autowired
    private IAbrEventService eventService;

    /**
     * 本人的订阅设置（marketing：是否接收营销消息）
     */
    @RateLimiter(key = "app_subscription_read:", time = 60, count = 60, limitType = LimitType.IP)
    @GetMapping
    public AjaxResult get(@RequestHeader(value = IAbrVisitorService.HEADER, required = false) String token)
    {
        AbrVisitor visitor = visitorService.resolveVisitor(token);
        return success(Map.of("marketing", !guardService.marketingOptedOut(AbrGrowthTask.TARGET_VISITOR, visitor.getVisitorId())));
    }

    /**
     * 修改订阅设置（body：marketing true 接收 / false 退订）
     */
    @RateLimiter(key = "app_subscription_write:", time = 60, count = 20, limitType = LimitType.IP)
    @PutMapping
    public AjaxResult put(@RequestHeader(value = IAbrVisitorService.HEADER, required = false) String token, @RequestBody Map<String, Object> body)
    {
        AbrVisitor visitor = visitorService.resolveVisitor(token);
        Object marketing = body.get("marketing");
        if (!(marketing instanceof Boolean))
        {
            return error("请选择是否接收营销消息");
        }
        boolean optout = !((Boolean) marketing);
        if (guardService.setMarketingOptout(AbrGrowthTask.TARGET_VISITOR, visitor.getVisitorId(), optout, "user", "visitor", null))
        {
            eventService.recordAction(visitor, optout ? "marketing_unsubscribed" : "marketing_resubscribed", null, null, null);
        }
        return success(Map.of("marketing", !optout));
    }
}
