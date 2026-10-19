package com.ygb.web.controller.abroad;

import java.util.Date;
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
import com.ygb.abroad.domain.AbrPublishRecord;
import com.ygb.abroad.service.IAbrPublishService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;
import com.ygb.common.utils.DateUtils;

/**
 * 内容引擎-发布记录（安排发布、发布后回填链接、失败与撤回、一键发布包）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/content/publish")
public class AbrPublishRecordController extends BaseController
{
    @Autowired
    private IAbrPublishService publishService;

    /**
     * 查询发布记录
     */
    @PreAuthorize("@ss.hasPermi('abroad:publish:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrPublishRecord record)
    {
        startPage();
        List<AbrPublishRecord> list = publishService.selectAbrPublishRecordList(record);
        return getDataTable(list);
    }

    /**
     * 获取发布记录详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:publish:query')")
    @GetMapping(value = "/{recordId}")
    public AjaxResult getInfo(@PathVariable Long recordId)
    {
        return success(publishService.selectAbrPublishRecordById(recordId));
    }

    /**
     * 一键发布包
     */
    @PreAuthorize("@ss.hasPermi('abroad:publish:query')")
    @GetMapping(value = "/package/{recordId}")
    public AjaxResult publishPackage(@PathVariable Long recordId)
    {
        return success(publishService.publishPackage(recordId));
    }

    /**
     * 安排发布（body：contentId、plannedTime yyyy-MM-dd HH:mm:ss，为空为现在）
     */
    @PreAuthorize("@ss.hasPermi('abroad:publish:add')")
    @Log(title = "安排发布", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult schedule(@RequestBody Map<String, Object> body)
    {
        Object contentId = body.get("contentId");
        if (!(contentId instanceof Number))
        {
            return error("请选择内容");
        }
        Date planned = time(body.get("plannedTime"));
        if (body.get("plannedTime") != null && planned == null)
        {
            return error("计划时间格式为 yyyy-MM-dd HH:mm:ss");
        }
        return success(publishService.schedule(((Number) contentId).longValue(), planned, getUsername()));
    }

    /**
     * 回填为已发布（body：postUrl、platformPostId、publishedTime）
     */
    @PreAuthorize("@ss.hasPermi('abroad:publish:edit')")
    @Log(title = "发布回填", businessType = BusinessType.UPDATE)
    @PutMapping("/done/{recordId}")
    public AjaxResult done(@PathVariable Long recordId, @RequestBody Map<String, Object> body)
    {
        AbrPublishRecord r = new AbrPublishRecord();
        r.setRecordId(recordId);
        r.setPostUrl((String) body.get("postUrl"));
        r.setPlatformPostId((String) body.get("platformPostId"));
        r.setPublishedTime(time(body.get("publishedTime")));
        if (body.get("publishedTime") != null && r.getPublishedTime() == null)
        {
            return error("发布时间格式为 yyyy-MM-dd HH:mm:ss");
        }
        return publishService.markPublished(r, getUsername()) ? success("已回填") : error("只有待发布或失败的记录可以回填");
    }

    /**
     * 标为失败（body：reason）
     */
    @PreAuthorize("@ss.hasPermi('abroad:publish:edit')")
    @Log(title = "发布失败", businessType = BusinessType.UPDATE)
    @PutMapping("/fail/{recordId}")
    public AjaxResult fail(@PathVariable Long recordId, @RequestBody Map<String, String> body)
    {
        return publishService.markFailed(recordId, body.get("reason"), getUsername()) ? success("已标为失败") : error("只有待发布的记录可以标为失败");
    }

    /**
     * 撤回（body：reason）
     */
    @PreAuthorize("@ss.hasPermi('abroad:publish:edit')")
    @Log(title = "发布撤回", businessType = BusinessType.UPDATE)
    @PutMapping("/withdraw/{recordId}")
    public AjaxResult withdraw(@PathVariable Long recordId, @RequestBody Map<String, String> body)
    {
        return publishService.withdraw(recordId, body.get("reason"), getUsername()) ? success("已撤回") : error("只有待发布或已发布的记录可以撤回");
    }

    /**
     * 解析 yyyy-MM-dd HH:mm:ss（为空或格式不对返回 null）
     */
    private static Date time(Object value)
    {
        if (!(value instanceof String s) || s.isBlank() || !s.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$"))
        {
            return null;
        }
        return DateUtils.dateTime(DateUtils.YYYY_MM_DD_HH_MM_SS, s);
    }
}
