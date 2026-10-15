package com.ygb.web.controller.app;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.service.IAbrPlanService;
import com.ygb.common.annotation.Anonymous;
import com.ygb.common.annotation.RateLimiter;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.enums.LimitType;

/**
 * C 端-分享查看（家长只读视图）
 * 
 * 匿名接口：凭分享码查看，不需要访客令牌；分享码可被分享人随时撤销；按 IP 限流。
 * 
 * @author ygb
 */
@Anonymous
@RestController
@RequestMapping("/app/share")
public class AppShareController extends BaseController
{
    @Autowired
    private IAbrPlanService planService;

    /**
     * 家长只读视图：学生的选校单、最近截止与材料完成度（不含分数等个人信息与联系方式）
     */
    @RateLimiter(key = "app_share_view:", time = 60, count = 30, limitType = LimitType.IP)
    @GetMapping("/plan/{code}")
    public AjaxResult plan(@PathVariable String code)
    {
        return success(planService.selectSharedPlan(code));
    }
}
