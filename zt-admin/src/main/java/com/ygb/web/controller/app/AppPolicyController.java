package com.ygb.web.controller.app;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.service.IAbrPolicyService;
import com.ygb.common.annotation.Anonymous;
import com.ygb.common.annotation.RateLimiter;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.enums.LimitType;

/**
 * C 端-协议（隐私政策等）：未登录、未同意前也要能阅读，匿名开放
 * 
 * @author ygb
 */
@Anonymous
@RestController
@RequestMapping("/app/policy")
public class AppPolicyController extends BaseController
{
    @Autowired
    private IAbrPolicyService policyService;

    /**
     * 取协议当前启用的版本
     */
    @RateLimiter(key = "app_policy:", time = 60, count = 60, limitType = LimitType.IP)
    @GetMapping("/{policyCode}")
    public AjaxResult current(@PathVariable String policyCode)
    {
        return success(policyService.selectActivePolicy(policyCode));
    }
}
