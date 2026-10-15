package com.ygb.web.controller.app;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppQuickBody;
import com.ygb.abroad.service.IAbrQuickToolService;
import com.ygb.abroad.service.IAbrVisitorService;
import com.ygb.common.annotation.Anonymous;
import com.ygb.common.annotation.RateLimiter;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.enums.LimitType;

/**
 * C 端-入口工具（30 秒快测；其余工具在 M4-03）
 * 
 * 匿名可用：身份来自请求头 X-Visitor-Token；核心结果不设留资墙；按 IP 限流。
 * 
 * @author ygb
 */
@Anonymous
@RestController
@RequestMapping("/app/tool")
public class AppToolController extends BaseController
{
    @Autowired
    private IAbrQuickToolService quickToolService;

    @Autowired
    private IAbrVisitorService visitorService;

    /**
     * 快测输入页：题目与国家 / 地区开放情况
     */
    @RateLimiter(key = "app_tool_form:", time = 60, count = 60, limitType = LimitType.IP)
    @GetMapping("/quick/form")
    public AjaxResult quickForm(@RequestParam(value = "degree", required = false) String degree)
    {
        return success(quickToolService.selectForm(degree));
    }

    /**
     * 快测计算
     */
    @RateLimiter(key = "app_tool_quick:", time = 60, count = 20, limitType = LimitType.IP)
    @PostMapping("/quick")
    public AjaxResult quick(@RequestHeader(value = IAbrVisitorService.HEADER, required = false) String token,
            @Validated @RequestBody AppQuickBody body)
    {
        AbrVisitor visitor = visitorService.resolveVisitor(token);
        return success(quickToolService.quick(visitor, body));
    }
}
