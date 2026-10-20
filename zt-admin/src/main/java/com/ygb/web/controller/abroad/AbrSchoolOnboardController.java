package com.ygb.web.controller.abroad;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.service.IAbrRegistryOnboardService;
import com.ygb.abroad.service.IAbrSchoolOnboardService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.enums.BusinessType;

/**
 * 院校自动建档（M2-12 美国全国性大学；M2-13 第 1 批：英国、中国香港、新加坡、澳洲）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/school/onboard")
public class AbrSchoolOnboardController extends BaseController
{
    @Autowired
    private IAbrSchoolOnboardService onboardService;

    @Autowired
    private IAbrRegistryOnboardService registryOnboardService;

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
     * 后台建立 / 更新第 1 批（英国、中国香港、新加坡、澳洲）学校档案（按各国官方名录核对，可重复执行）
     */
    @PreAuthorize("@ss.hasPermi('abroad:school:add')")
    @Log(title = "第 1 批院校建档", businessType = BusinessType.INSERT)
    @PostMapping("/batch1")
    public AjaxResult batch1()
    {
        return registryOnboardService.start(getUsername()) ? success("已开始，稍后查看进度") : error("建档正在进行中");
    }

    /**
     * 最近一次建档的进度与结果（job：us 美国 / batch1 第 1 批）
     */
    @PreAuthorize("@ss.hasPermi('abroad:school:list')")
    @GetMapping("/status")
    public AjaxResult status(@RequestParam(defaultValue = "us") String job)
    {
        return success("batch1".equals(job) ? registryOnboardService.status() : onboardService.status());
    }
}
