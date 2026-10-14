package com.ygb.web.controller.app;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.vo.AppVisitorBody;
import com.ygb.abroad.service.IAbrVisitorService;
import com.ygb.common.annotation.Anonymous;
import com.ygb.common.annotation.RateLimiter;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.enums.LimitType;

/**
 * C 端-访客（匿名身份）
 * 
 * 匿名接口：学生打开落地页即调用，按 IP 限流；只返回访客令牌，不返回任何档案数据。
 * 
 * @author ygb
 */
@Anonymous
@RestController
@RequestMapping("/app/visitor")
public class AppVisitorController extends BaseController
{
    @Autowired
    private IAbrVisitorService visitorService;

    /**
     * 进入 C 端：沿用已有访客令牌，或新建访客并返回令牌
     */
    @RateLimiter(key = "app_visitor:", time = 60, count = 20, limitType = LimitType.IP)
    @PostMapping
    public AjaxResult enter(@RequestHeader(value = IAbrVisitorService.HEADER, required = false) String token,
            @RequestBody(required = false) AppVisitorBody body)
    {
        return success().put("visitorToken", visitorService.enterVisitor(token, body));
    }
}
