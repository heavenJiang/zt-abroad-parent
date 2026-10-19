package com.ygb.web.controller.abroad;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrConsultant;
import com.ygb.abroad.domain.AbrLeadAssignSetting;
import com.ygb.abroad.service.IAbrLeadAssignService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.enums.BusinessType;

/**
 * 获客运营-线索分配（分配设置、参与分配的顾问）与员工提醒
 *
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/leadAssign")
public class AbrLeadAssignController extends BaseController
{
    @Autowired
    private IAbrLeadAssignService assignService;

    /**
     * 分配设置
     */
    @PreAuthorize("@ss.hasPermi('abroad:leadAssign:list')")
    @GetMapping("/setting")
    public AjaxResult setting()
    {
        return success(assignService.selectSetting());
    }

    /**
     * 修改分配设置
     */
    @PreAuthorize("@ss.hasPermi('abroad:leadAssign:edit')")
    @Log(title = "线索分配设置", businessType = BusinessType.UPDATE)
    @PutMapping("/setting")
    public AjaxResult updateSetting(@RequestBody AbrLeadAssignSetting setting)
    {
        setting.setUpdateBy(getUsername());
        return toAjax(assignService.updateSetting(setting));
    }

    /**
     * 参与分配的顾问（带进行中线索数）
     */
    @PreAuthorize("@ss.hasPermi('abroad:leadAssign:list')")
    @GetMapping("/consultant/list")
    public AjaxResult consultants()
    {
        return success(assignService.selectConsultants());
    }

    /**
     * 新增顾问
     */
    @PreAuthorize("@ss.hasPermi('abroad:leadAssign:edit')")
    @Log(title = "线索分配顾问", businessType = BusinessType.INSERT)
    @PostMapping("/consultant")
    public AjaxResult addConsultant(@RequestBody AbrConsultant consultant)
    {
        consultant.setConsultantId(null);
        consultant.setCreateBy(getUsername());
        return toAjax(assignService.saveConsultant(consultant));
    }

    /**
     * 修改顾问
     */
    @PreAuthorize("@ss.hasPermi('abroad:leadAssign:edit')")
    @Log(title = "线索分配顾问", businessType = BusinessType.UPDATE)
    @PutMapping("/consultant")
    public AjaxResult editConsultant(@RequestBody AbrConsultant consultant)
    {
        consultant.setUpdateBy(getUsername());
        return toAjax(assignService.saveConsultant(consultant));
    }

    /**
     * 删除顾问
     */
    @PreAuthorize("@ss.hasPermi('abroad:leadAssign:edit')")
    @Log(title = "线索分配顾问", businessType = BusinessType.DELETE)
    @DeleteMapping("/consultant/{consultantIds}")
    public AjaxResult removeConsultants(@PathVariable Long[] consultantIds)
    {
        return toAjax(assignService.deleteConsultants(consultantIds));
    }

    /**
     * 我的提醒（unread=true 只看未读）
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:list')")
    @GetMapping("/notice")
    public AjaxResult notices(@RequestParam(defaultValue = "false") boolean unread)
    {
        return success(assignService.myNotices(getUserId(), unread)).put("unread", assignService.unreadCount(getUserId()));
    }

    /**
     * 标为已读（ids 为空时全部已读）
     */
    @PreAuthorize("@ss.hasPermi('abroad:lead:list')")
    @PutMapping("/notice/read")
    public AjaxResult read(@RequestBody(required = false) Long[] ids)
    {
        return toAjax(assignService.markRead(getUserId(), ids == null ? new Long[0] : ids));
    }
}
