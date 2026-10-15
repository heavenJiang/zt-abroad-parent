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
import com.ygb.abroad.domain.AbrMaterialTemplate;
import com.ygb.abroad.service.IAbrMaterialTemplateService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 院校库-默认材料模板（每个「国家 / 地区 × 学位」一份；项目没有写材料要求时，材料清单按模板列出）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/materialTemplate")
public class AbrMaterialTemplateController extends BaseController
{
    @Autowired
    private IAbrMaterialTemplateService templateService;

    /**
     * 查询模板列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:materialTemplate:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrMaterialTemplate template)
    {
        startPage();
        List<AbrMaterialTemplate> list = templateService.selectAbrMaterialTemplateList(template);
        return getDataTable(list);
    }

    /**
     * 获取模板详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:materialTemplate:query')")
    @GetMapping(value = "/{templateId}")
    public AjaxResult getInfo(@PathVariable Long templateId)
    {
        return success(templateService.selectAbrMaterialTemplateById(templateId));
    }

    /**
     * 新增模板
     */
    @PreAuthorize("@ss.hasPermi('abroad:materialTemplate:add')")
    @Log(title = "默认材料模板", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody AbrMaterialTemplate template)
    {
        template.setCreateBy(getUsername());
        return toAjax(templateService.insertAbrMaterialTemplate(template));
    }

    /**
     * 修改模板
     */
    @PreAuthorize("@ss.hasPermi('abroad:materialTemplate:edit')")
    @Log(title = "默认材料模板", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody AbrMaterialTemplate template)
    {
        template.setUpdateBy(getUsername());
        return toAjax(templateService.updateAbrMaterialTemplate(template));
    }

    /**
     * 删除模板
     */
    @PreAuthorize("@ss.hasPermi('abroad:materialTemplate:remove')")
    @Log(title = "默认材料模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/{templateIds}")
    public AjaxResult remove(@PathVariable Long[] templateIds)
    {
        return toAjax(templateService.deleteAbrMaterialTemplateByIds(templateIds));
    }
}
