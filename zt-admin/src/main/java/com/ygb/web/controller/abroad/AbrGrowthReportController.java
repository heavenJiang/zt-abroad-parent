package com.ygb.web.controller.abroad;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrGrowthParamLog;
import com.ygb.abroad.domain.AbrGrowthReport;
import com.ygb.abroad.service.IAbrGrowthAnalystService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 增长周报与调参记录（M9-04 growth_analyst）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/growthReport")
public class AbrGrowthReportController extends BaseController
{
    @Autowired
    private IAbrGrowthAnalystService analystService;

    /**
     * 周报列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:growthReport:list')")
    @GetMapping("/list")
    public TableDataInfo list()
    {
        startPage();
        List<AbrGrowthReport> list = analystService.selectReportList();
        return getDataTable(list);
    }

    /**
     * 周报详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:growthReport:list')")
    @GetMapping("/{reportId}")
    public AjaxResult getInfo(@PathVariable Long reportId)
    {
        return success(analystService.selectReportById(reportId));
    }

    /**
     * 立即生成（重新生成）某一周的周报；tune=true 时该周还没调过参则调参
     */
    @PreAuthorize("@ss.hasPermi('abroad:growthReport:run')")
    @Log(title = "增长周报生成", businessType = BusinessType.OTHER)
    @PostMapping("/generate")
    public AjaxResult generate(@RequestBody Map<String, Object> body)
    {
        Object week = body.get("weekStart");
        boolean tune = Boolean.TRUE.equals(body.get("tune"));
        return success(analystService.weekly(week == null ? null : String.valueOf(week), tune, getUsername()));
    }

    /**
     * 立即评估 A/B 实验
     */
    @PreAuthorize("@ss.hasPermi('abroad:growthReport:run')")
    @Log(title = "A/B 实验评估", businessType = BusinessType.OTHER)
    @PostMapping("/evaluate")
    public AjaxResult evaluate()
    {
        return success(analystService.evaluateExperiments(getUsername()));
    }

    /**
     * 调参记录
     */
    @PreAuthorize("@ss.hasPermi('abroad:growthReport:list')")
    @GetMapping("/param/list")
    public TableDataInfo paramList(AbrGrowthParamLog log)
    {
        startPage();
        List<AbrGrowthParamLog> list = analystService.selectParamLogList(log);
        return getDataTable(list);
    }

    /**
     * 调参处理：revert 撤销 / apply 采纳建议 / dismiss 忽略建议
     */
    @PreAuthorize("@ss.hasPermi('abroad:growthReport:param')")
    @Log(title = "调参处理", businessType = BusinessType.UPDATE)
    @PutMapping("/param/{logId}")
    public AjaxResult param(@PathVariable Long logId, @RequestBody Map<String, String> body)
    {
        String action = body.get("action");
        if ("revert".equals(action))
        {
            return toAjax(analystService.revertParam(logId, getUsername()));
        }
        if ("apply".equals(action))
        {
            return toAjax(analystService.applyParam(logId, getUsername()));
        }
        if ("dismiss".equals(action))
        {
            return toAjax(analystService.dismissParam(logId, getUsername()));
        }
        return error("不支持的操作");
    }
}
