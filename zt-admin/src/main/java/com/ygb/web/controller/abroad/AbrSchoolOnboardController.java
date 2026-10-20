package com.ygb.web.controller.abroad;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.service.IAbrSchoolOnboardService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.enums.BusinessType;

/**
 * 院校自动建档（M2-12 美国全国性大学）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/school/onboard")
public class AbrSchoolOnboardController extends BaseController
{
    @Autowired
    private IAbrSchoolOnboardService onboardService;

    /**
     * 后台建立 / 更新美国全国性大学学校档案（可重复执行，已有的只更新有变化的字段）
     */
    @PreAuthorize("@ss.hasPermi('abroad:school:add')")
    @Log(title = "美国院校建档", businessType = BusinessType.INSERT)
    @PostMapping("/us")
    public AjaxResult us()
    {
        return onboardService.startUsNational(getUsername()) ? success("已开始，稍后查看进度") : error("建档正在进行中");
    }

    /**
     * 最近一次建档的进度与结果
     */
    @PreAuthorize("@ss.hasPermi('abroad:school:list')")
    @GetMapping("/status")
    public AjaxResult status()
    {
        return success(onboardService.status());
    }
}
