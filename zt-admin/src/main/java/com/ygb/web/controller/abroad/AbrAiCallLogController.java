package com.ygb.web.controller.abroad;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrAiCallLog;
import com.ygb.abroad.service.IAbrAiCallLogService;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;

/**
 * AI 管理-调用日志
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/aiCallLog")
public class AbrAiCallLogController extends BaseController
{
    @Autowired
    private IAbrAiCallLogService callLogService;

    /**
     * 查询调用日志列表（按 Agent、状态、时间筛选）
     */
    @PreAuthorize("@ss.hasPermi('abroad:aiCallLog:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrAiCallLog log)
    {
        startPage();
        List<AbrAiCallLog> list = callLogService.selectAbrAiCallLogList(log);
        return getDataTable(list);
    }

    /**
     * 每日 / 每个 Agent 的调用次数、token 与费用（日期按北京时间，含首尾）
     */
    @PreAuthorize("@ss.hasPermi('abroad:aiCallLog:list')")
    @GetMapping("/stats")
    public AjaxResult stats(@RequestParam(required = false) String beginTime, @RequestParam(required = false) String endTime)
    {
        return success(callLogService.selectAbrAiUsageStats(beginTime, endTime));
    }

    /**
     * 获取调用日志详情（含去标识化的输入摘要与输出）
     */
    @PreAuthorize("@ss.hasPermi('abroad:aiCallLog:query')")
    @GetMapping(value = "/{callId}")
    public AjaxResult getInfo(@PathVariable Long callId)
    {
        return success(callLogService.selectAbrAiCallLogById(callId));
    }
}
