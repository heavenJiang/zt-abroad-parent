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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrTopic;
import com.ygb.abroad.service.IAbrContentWriterService;
import com.ygb.abroad.service.IAbrTopicService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 内容自动化-选题排期（topic_planner 生成；员工可随时插入、修改、取消）
 *
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/content/topic")
public class AbrTopicController extends BaseController
{
    @Autowired
    private IAbrTopicService topicService;

    @Autowired
    private IAbrContentWriterService writerService;

    /**
     * 查询选题列表（params[beginDate] / params[endDate] 为计划日期区间）
     */
    @PreAuthorize("@ss.hasPermi('abroad:topic:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrTopic topic)
    {
        startPage();
        List<AbrTopic> list = topicService.selectAbrTopicList(topic);
        return getDataTable(list);
    }

    /**
     * 获取选题详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:topic:query')")
    @GetMapping("/{topicId}")
    public AjaxResult getInfo(@PathVariable Long topicId)
    {
        return success(topicService.selectAbrTopicById(topicId));
    }

    /**
     * 插入选题
     */
    @PreAuthorize("@ss.hasPermi('abroad:topic:add')")
    @Log(title = "选题排期", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody AbrTopic topic)
    {
        topic.setCreateBy(getUsername());
        return toAjax(topicService.insertAbrTopic(topic));
    }

    /**
     * 修改选题（含取消、跳过）
     */
    @PreAuthorize("@ss.hasPermi('abroad:topic:edit')")
    @Log(title = "选题排期", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody AbrTopic topic)
    {
        topic.setUpdateBy(getUsername());
        return toAjax(topicService.updateAbrTopic(topic));
    }

    /**
     * 删除还没写稿的选题
     */
    @PreAuthorize("@ss.hasPermi('abroad:topic:remove')")
    @Log(title = "选题排期", businessType = BusinessType.DELETE)
    @DeleteMapping("/{topicIds}")
    public AjaxResult remove(@PathVariable Long[] topicIds)
    {
        return toAjax(topicService.deleteAbrTopicByIds(topicIds));
    }

    /**
     * 立即排期（accountId 为空时全部在用账号；replan=true 时先取消剧本已不再生效的待写稿选题）
     */
    @PreAuthorize("@ss.hasPermi('abroad:topic:plan')")
    @Log(title = "选题排期", businessType = BusinessType.OTHER)
    @PostMapping("/plan")
    public AjaxResult plan(@RequestParam(required = false) Long accountId, @RequestParam(defaultValue = "false") boolean replan)
    {
        return success(topicService.plan(accountId, replan, getUsername()));
    }

    /**
     * 立即为一条选题写稿（content_writer；写成的内容进入合规闸门）
     */
    @PreAuthorize("@ss.hasPermi('abroad:topic:write')")
    @Log(title = "选题排期", businessType = BusinessType.OTHER)
    @PostMapping("/write/{topicId}")
    public AjaxResult write(@PathVariable Long topicId)
    {
        return success(writerService.writeTopic(topicId, getUsername()));
    }

    /**
     * 立即写到期的选题
     */
    @PreAuthorize("@ss.hasPermi('abroad:topic:write')")
    @Log(title = "选题排期", businessType = BusinessType.OTHER)
    @PostMapping("/write")
    public AjaxResult writeDue()
    {
        return success(writerService.writeDue(getUsername()));
    }
}
