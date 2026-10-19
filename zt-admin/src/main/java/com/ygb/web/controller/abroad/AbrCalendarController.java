package com.ygb.web.controller.abroad;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrCalendarEntry;
import com.ygb.abroad.growth.MarketingCalendar;
import com.ygb.abroad.service.IAbrCalendarService;
import com.ygb.abroad.service.IAbrOrchestratorService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;
import com.ygb.common.utils.StringUtils;

/**
 * 营销编排器-营销日历（时间窗决定剧本何时生效，按北京时间自动切换；节点当天发出事件）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/orchestrator/calendar")
public class AbrCalendarController extends BaseController
{
    @Autowired
    private IAbrCalendarService calendarService;

    @Autowired
    private IAbrOrchestratorService orchestratorService;

    /**
     * 查询日历条目列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:calendar:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrCalendarEntry entry)
    {
        startPage();
        List<AbrCalendarEntry> list = calendarService.selectAbrCalendarEntryList(entry);
        return getDataTable(list);
    }

    /**
     * 今天的编排概况：生效剧本、节点、有处理器的 Agent、任务数、定时规则下次触发时间
     */
    @PreAuthorize("@ss.hasPermi('abroad:calendar:list')")
    @GetMapping("/current")
    public AjaxResult current()
    {
        return success(orchestratorService.current());
    }

    /**
     * 预览一段时间内的剧本切换与节点（from 默认今天，days 默认 365）
     */
    @PreAuthorize("@ss.hasPermi('abroad:calendar:list')")
    @GetMapping("/preview")
    public AjaxResult preview(@RequestParam(required = false) String from, @RequestParam(defaultValue = "365") int days)
    {
        LocalDate start;
        try
        {
            start = StringUtils.isBlank(from) ? MarketingCalendar.today() : LocalDate.parse(from);
        }
        catch (DateTimeParseException e)
        {
            return error("开始日期格式应为 yyyy-MM-dd");
        }
        return success(orchestratorService.preview(start, days));
    }

    /**
     * 获取日历条目详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:calendar:query')")
    @GetMapping(value = "/{entryId}")
    public AjaxResult getInfo(@PathVariable Long entryId)
    {
        return success(calendarService.selectAbrCalendarEntryById(entryId));
    }

    /**
     * 新增日历条目
     */
    @PreAuthorize("@ss.hasPermi('abroad:calendar:add')")
    @Log(title = "营销日历", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody AbrCalendarEntry entry)
    {
        entry.setCreateBy(getUsername());
        return toAjax(calendarService.insertAbrCalendarEntry(entry));
    }

    /**
     * 修改日历条目
     */
    @PreAuthorize("@ss.hasPermi('abroad:calendar:edit')")
    @Log(title = "营销日历", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody AbrCalendarEntry entry)
    {
        entry.setUpdateBy(getUsername());
        return toAjax(calendarService.updateAbrCalendarEntry(entry));
    }

    /**
     * 删除日历条目
     */
    @PreAuthorize("@ss.hasPermi('abroad:calendar:remove')")
    @Log(title = "营销日历", businessType = BusinessType.DELETE)
    @DeleteMapping("/{entryIds}")
    public AjaxResult remove(@PathVariable Long[] entryIds)
    {
        return toAjax(calendarService.deleteAbrCalendarEntryByIds(entryIds));
    }
}
