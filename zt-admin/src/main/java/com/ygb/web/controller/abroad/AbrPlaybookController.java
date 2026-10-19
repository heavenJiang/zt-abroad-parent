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
import com.ygb.abroad.domain.AbrPlaybook;
import com.ygb.abroad.service.IAbrPlaybookService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 营销编排器-营销剧本（写什么、推什么工具、对谁触达；由营销日历决定何时生效）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/orchestrator/playbook")
public class AbrPlaybookController extends BaseController
{
    @Autowired
    private IAbrPlaybookService playbookService;

    /**
     * 查询剧本列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:playbook:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrPlaybook playbook)
    {
        startPage();
        List<AbrPlaybook> list = playbookService.selectAbrPlaybookList(playbook);
        return getDataTable(list);
    }

    /**
     * 获取剧本详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:playbook:query')")
    @GetMapping(value = "/{playbookId}")
    public AjaxResult getInfo(@PathVariable Long playbookId)
    {
        return success(playbookService.selectAbrPlaybookById(playbookId));
    }

    /**
     * 新增剧本
     */
    @PreAuthorize("@ss.hasPermi('abroad:playbook:add')")
    @Log(title = "营销剧本", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody AbrPlaybook playbook)
    {
        if (!playbookService.checkPlaybookCodeUnique(playbook))
        {
            return error("新增剧本'" + playbook.getPlaybookName() + "'失败，剧本编码已存在");
        }
        playbook.setCreateBy(getUsername());
        return toAjax(playbookService.insertAbrPlaybook(playbook));
    }

    /**
     * 修改剧本
     */
    @PreAuthorize("@ss.hasPermi('abroad:playbook:edit')")
    @Log(title = "营销剧本", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody AbrPlaybook playbook)
    {
        if (!playbookService.checkPlaybookCodeUnique(playbook))
        {
            return error("修改剧本'" + playbook.getPlaybookName() + "'失败，剧本编码已存在");
        }
        playbook.setUpdateBy(getUsername());
        return toAjax(playbookService.updateAbrPlaybook(playbook));
    }

    /**
     * 删除剧本
     */
    @PreAuthorize("@ss.hasPermi('abroad:playbook:remove')")
    @Log(title = "营销剧本", businessType = BusinessType.DELETE)
    @DeleteMapping("/{playbookIds}")
    public AjaxResult remove(@PathVariable Long[] playbookIds)
    {
        return toAjax(playbookService.deleteAbrPlaybookByIds(playbookIds));
    }
}
