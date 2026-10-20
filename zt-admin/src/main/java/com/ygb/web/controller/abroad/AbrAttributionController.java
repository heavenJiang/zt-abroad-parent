package com.ygb.web.controller.abroad;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.service.IAbrAttributionService;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;

/**
 * 归因看板（M9-03）：获客漏斗按渠道 / 账号 / 内容 / 剧本，每周活跃选校单与留存
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/attribution")
public class AbrAttributionController extends BaseController
{
    @Autowired
    private IAbrAttributionService attributionService;

    /**
     * 获客漏斗
     */
    @PreAuthorize("@ss.hasPermi('abroad:attribution:list')")
    @GetMapping("/funnel")
    public AjaxResult funnel(@RequestParam(required = false) String beginDate, @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String groupBy)
    {
        return success(attributionService.funnel(beginDate, endDate, groupBy));
    }

    /**
     * 产品指标：每周活跃选校单、留存、分享率、自然推荐占比
     */
    @PreAuthorize("@ss.hasPermi('abroad:attribution:list')")
    @GetMapping("/product")
    public AjaxResult product(@RequestParam(required = false) Integer weeks)
    {
        return success(attributionService.product(weeks));
    }
}
