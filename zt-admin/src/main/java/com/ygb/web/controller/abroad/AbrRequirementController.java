package com.ygb.web.controller.abroad;

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
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.service.IAbrProgramService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.enums.BusinessType;

/**
 * 院校库-项目申请要求（按申请季，含字段来源；权限沿用项目）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/requirement")
public class AbrRequirementController extends BaseController
{
    @Autowired
    private IAbrProgramService programService;

    /**
     * 查询项目的全部申请要求（每条带字段来源）
     */
    @PreAuthorize("@ss.hasPermi('abroad:program:query')")
    @GetMapping("/list")
    public AjaxResult list(@RequestParam Long programId)
    {
        return success(programService.selectAbrRequirementByProgramId(programId));
    }

    /**
     * 获取申请要求详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:program:query')")
    @GetMapping(value = "/{requirementId}")
    public AjaxResult getInfo(@PathVariable Long requirementId)
    {
        return success(programService.selectAbrRequirementById(requirementId));
    }

    /**
     * 新增申请要求
     */
    @PreAuthorize("@ss.hasPermi('abroad:program:edit')")
    @Log(title = "项目申请要求", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody AbrRequirement requirement)
    {
        if (!programService.checkAbrRequirementUnique(requirement))
        {
            return error("新增失败，申请季'" + requirement.getSeason() + "'的要求已存在");
        }
        requirement.setCreateBy(getUsername());
        return toAjax(programService.insertAbrRequirement(requirement));
    }

    /**
     * 修改申请要求
     */
    @PreAuthorize("@ss.hasPermi('abroad:program:edit')")
    @Log(title = "项目申请要求", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody AbrRequirement requirement)
    {
        if (!programService.checkAbrRequirementUnique(requirement))
        {
            return error("修改失败，申请季'" + requirement.getSeason() + "'的要求已存在");
        }
        requirement.setUpdateBy(getUsername());
        return toAjax(programService.updateAbrRequirement(requirement));
    }

    /**
     * 删除申请要求
     */
    @PreAuthorize("@ss.hasPermi('abroad:program:edit')")
    @Log(title = "项目申请要求", businessType = BusinessType.DELETE)
    @DeleteMapping("/{requirementIds}")
    public AjaxResult remove(@PathVariable Long[] requirementIds)
    {
        return toAjax(programService.deleteAbrRequirementByIds(requirementIds, getUsername()));
    }
}
