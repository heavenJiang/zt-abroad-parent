package com.ygb.web.controller.abroad;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrBreakerRule;
import com.ygb.abroad.domain.AbrBreakerTrip;
import com.ygb.abroad.domain.AbrGrowthSignal;
import com.ygb.abroad.service.IAbrGrowthControlService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 营销编排器-熔断（规则、信号、触发记录；触发即暂停，恢复在 Agent 运行页）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/orchestrator/breaker")
public class AbrBreakerController extends BaseController
{
    @Autowired
    private IAbrGrowthControlService controlService;

    /**
     * 查询熔断规则列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:breaker:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrBreakerRule rule)
    {
        startPage();
        List<AbrBreakerRule> list = controlService.selectRuleList(rule);
        return getDataTable(list);
    }

    /**
     * 熔断与预算闸触发记录
     */
    @PreAuthorize("@ss.hasPermi('abroad:breaker:list')")
    @GetMapping("/trip/list")
    public TableDataInfo trips(AbrBreakerTrip trip)
    {
        startPage();
        List<AbrBreakerTrip> list = controlService.selectTripList(trip);
        return getDataTable(list);
    }

    /**
     * 熔断信号
     */
    @PreAuthorize("@ss.hasPermi('abroad:breaker:list')")
    @GetMapping("/signal/list")
    public TableDataInfo signals(AbrGrowthSignal signal)
    {
        startPage();
        List<AbrGrowthSignal> list = controlService.selectSignalList(signal);
        return getDataTable(list);
    }

    /**
     * 录入信号（投诉、平台违规提示、差评回复；熔断演练也用它）
     */
    @PreAuthorize("@ss.hasPermi('abroad:breaker:signal')")
    @Log(title = "熔断信号", businessType = BusinessType.INSERT)
    @PostMapping("/signal")
    public AjaxResult addSignal(@Validated @RequestBody AbrGrowthSignal signal)
    {
        return toAjax(controlService.insertManualSignal(signal, getUsername()));
    }

    /**
     * 获取熔断规则详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:breaker:query')")
    @GetMapping(value = "/{ruleId}")
    public AjaxResult getInfo(@PathVariable Long ruleId)
    {
        return success(controlService.selectRuleById(ruleId));
    }

    /**
     * 新增熔断规则
     */
    @PreAuthorize("@ss.hasPermi('abroad:breaker:add')")
    @Log(title = "熔断规则", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody AbrBreakerRule rule)
    {
        if (!controlService.checkRuleCodeUnique(rule))
        {
            return error("新增熔断规则'" + rule.getRuleName() + "'失败，规则编码已存在");
        }
        rule.setCreateBy(getUsername());
        return toAjax(controlService.insertRule(rule));
    }

    /**
     * 修改熔断规则
     */
    @PreAuthorize("@ss.hasPermi('abroad:breaker:edit')")
    @Log(title = "熔断规则", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody AbrBreakerRule rule)
    {
        if (!controlService.checkRuleCodeUnique(rule))
        {
            return error("修改熔断规则'" + rule.getRuleName() + "'失败，规则编码已存在");
        }
        rule.setUpdateBy(getUsername());
        return toAjax(controlService.updateRule(rule));
    }

    /**
     * 删除熔断规则
     */
    @PreAuthorize("@ss.hasPermi('abroad:breaker:remove')")
    @Log(title = "熔断规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ruleIds}")
    public AjaxResult remove(@PathVariable Long[] ruleIds)
    {
        return toAjax(controlService.deleteRuleByIds(ruleIds));
    }
}
