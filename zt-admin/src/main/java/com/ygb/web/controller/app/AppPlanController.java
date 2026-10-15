package com.ygb.web.controller.app;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppPlanItemBody;
import com.ygb.abroad.service.IAbrPlanService;
import com.ygb.abroad.service.IAbrVisitorService;
import com.ygb.common.annotation.Anonymous;
import com.ygb.common.annotation.RateLimiter;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.enums.LimitType;

/**
 * C 端-我的选校单（匿名按访客保存，M4-04 登录后同步到学生账号）
 * 
 * @author ygb
 */
@Anonymous
@RestController
@RequestMapping("/app/plan")
public class AppPlanController extends BaseController
{
    @Autowired
    private IAbrPlanService planService;

    @Autowired
    private IAbrVisitorService visitorService;

    /**
     * 本人的选校单（按最新背景档案计算当前档位、逐项达标与档位分布提示）
     */
    @RateLimiter(key = "app_plan_read:", time = 60, count = 120, limitType = LimitType.IP)
    @GetMapping
    public AjaxResult plan(@RequestHeader(value = IAbrVisitorService.HEADER, required = false) String token)
    {
        AbrVisitor visitor = visitorService.resolveVisitor(token);
        return success(planService.selectPlan(visitor));
    }

    /**
     * 本人的选校单条目（不计算，只列加入时的档位）
     */
    @RateLimiter(key = "app_plan_read:", time = 60, count = 120, limitType = LimitType.IP)
    @GetMapping("/items")
    public AjaxResult items(@RequestHeader(value = IAbrVisitorService.HEADER, required = false) String token)
    {
        AbrVisitor visitor = visitorService.resolveVisitor(token);
        return success(planService.selectPlanItems(visitor));
    }

    /**
     * 加入选校单
     */
    @RateLimiter(key = "app_plan_write:", time = 60, count = 60, limitType = LimitType.IP)
    @PostMapping("/items")
    public AjaxResult add(@RequestHeader(value = IAbrVisitorService.HEADER, required = false) String token,
            @Validated @RequestBody AppPlanItemBody body)
    {
        AbrVisitor visitor = visitorService.resolveVisitor(token);
        return success(planService.addPlanItem(visitor, body));
    }

    /**
     * 从选校单移除
     */
    @RateLimiter(key = "app_plan_write:", time = 60, count = 60, limitType = LimitType.IP)
    @DeleteMapping("/items/{programId}")
    public AjaxResult remove(@RequestHeader(value = IAbrVisitorService.HEADER, required = false) String token,
            @PathVariable Long programId)
    {
        AbrVisitor visitor = visitorService.resolveVisitor(token);
        return success(planService.removePlanItem(visitor, programId));
    }
}
