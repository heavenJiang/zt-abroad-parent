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
import com.ygb.abroad.domain.AbrGrowthTrigger;
import com.ygb.abroad.service.IAbrGrowthTriggerService;
import com.ygb.abroad.service.IAbrOrchestratorService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 营销编排器-触发规则（事件订阅或定时 → 交给某个 Agent 执行的任务）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/orchestrator/trigger")
public class AbrGrowthTriggerController extends BaseController
{
    @Autowired
    private IAbrGrowthTriggerService triggerService;

    @Autowired
    private IAbrOrchestratorService orchestratorService;

    /**
     * 查询触发规则列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:trigger:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrGrowthTrigger trigger)
    {
        startPage();
        List<AbrGrowthTrigger> list = triggerService.selectAbrGrowthTriggerList(trigger);
        return getDataTable(list);
    }

    /**
     * 获取触发规则详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:trigger:query')")
    @GetMapping(value = "/{triggerId}")
    public AjaxResult getInfo(@PathVariable Long triggerId)
    {
        return success(triggerService.selectAbrGrowthTriggerById(triggerId));
    }

    /**
     * 新增触发规则
     */
    @PreAuthorize("@ss.hasPermi('abroad:trigger:add')")
    @Log(title = "编排触发规则", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody AbrGrowthTrigger trigger)
    {
        trigger.setCreateBy(getUsername());
        return toAjax(triggerService.insertAbrGrowthTrigger(trigger));
    }

    /**
     * 修改触发规则
     */
    @PreAuthorize("@ss.hasPermi('abroad:trigger:edit')")
    @Log(title = "编排触发规则", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody AbrGrowthTrigger trigger)
    {
        trigger.setUpdateBy(getUsername());
        return toAjax(triggerService.updateAbrGrowthTrigger(trigger));
    }

    /**
     * 删除触发规则（已生成的任务保留）
     */
    @PreAuthorize("@ss.hasPermi('abroad:trigger:remove')")
    @Log(title = "编排触发规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{triggerIds}")
    public AjaxResult remove(@PathVariable Long[] triggerIds)
    {
        return toAjax(triggerService.deleteAbrGrowthTriggerByIds(triggerIds));
    }

    /**
     * 手动触发一次：立即生成一条待执行任务（试运行用）
     */
    @PreAuthorize("@ss.hasPermi('abroad:trigger:fire')")
    @Log(title = "编排触发规则", businessType = BusinessType.OTHER)
    @PostMapping("/fire/{triggerId}")
    public AjaxResult fire(@PathVariable Long triggerId)
    {
        return success(orchestratorService.fireTrigger(triggerId, getUsername()));
    }
}
