package com.ygb.web.controller.abroad;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrGrowthTask;
import com.ygb.abroad.service.IAbrGrowthTaskService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 营销编排器-编排任务队列（Agent 运行与对外触达；查看、重试、取消）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/orchestrator/task")
public class AbrGrowthTaskController extends BaseController
{
    @Autowired
    private IAbrGrowthTaskService taskService;

    /**
     * 查询任务列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:growthTask:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrGrowthTask task)
    {
        startPage();
        List<AbrGrowthTask> list = taskService.selectAbrGrowthTaskList(task);
        return getDataTable(list);
    }

    /**
     * 获取任务详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:growthTask:query')")
    @GetMapping(value = "/{taskId}")
    public AjaxResult getInfo(@PathVariable Long taskId)
    {
        return success(taskService.selectAbrGrowthTaskById(taskId));
    }

    /**
     * 重试：失败、跳过、过期、取消的任务恢复为待执行
     */
    @PreAuthorize("@ss.hasPermi('abroad:growthTask:retry')")
    @Log(title = "编排任务", businessType = BusinessType.UPDATE)
    @PutMapping("/retry/{taskIds}")
    public AjaxResult retry(@PathVariable Long[] taskIds)
    {
        int rows = taskService.retryAbrGrowthTasks(taskIds, getUsername());
        return rows > 0 ? success("已恢复 " + rows + " 条任务") : error("所选任务都不能重试（只能重试失败、跳过、过期、取消的任务）");
    }

    /**
     * 取消待执行的任务
     */
    @PreAuthorize("@ss.hasPermi('abroad:growthTask:cancel')")
    @Log(title = "编排任务", businessType = BusinessType.UPDATE)
    @PutMapping("/cancel/{taskIds}")
    public AjaxResult cancel(@PathVariable Long[] taskIds)
    {
        int rows = taskService.cancelAbrGrowthTasks(taskIds, getUsername());
        return rows > 0 ? success("已取消 " + rows + " 条任务") : error("所选任务都不能取消（只能取消待执行的任务）");
    }
}
