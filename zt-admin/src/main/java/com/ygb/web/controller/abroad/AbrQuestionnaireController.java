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
import com.ygb.abroad.domain.AbrQuestionnaire;
import com.ygb.abroad.domain.vo.AbrQuestionnaireBody;
import com.ygb.abroad.service.IAbrQuestionnaireService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 测评与选校-问卷版本（上传 JSON 导入为草稿，启用后生效；版本不可修改）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/questionnaire")
public class AbrQuestionnaireController extends BaseController
{
    @Autowired
    private IAbrQuestionnaireService questionnaireService;

    /**
     * 查询问卷版本列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:questionnaire:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrQuestionnaire questionnaire)
    {
        startPage();
        List<AbrQuestionnaire> list = questionnaireService.selectAbrQuestionnaireList(questionnaire);
        return getDataTable(list);
    }

    /**
     * 获取问卷版本详情（含问卷定义）
     */
    @PreAuthorize("@ss.hasPermi('abroad:questionnaire:query')")
    @GetMapping(value = "/{questionnaireId}")
    public AjaxResult getInfo(@PathVariable Long questionnaireId)
    {
        return success(questionnaireService.selectAbrQuestionnaireById(questionnaireId));
    }

    /**
     * 校验问卷 JSON（导入前预览，不保存）
     */
    @PreAuthorize("@ss.hasPermi('abroad:questionnaire:import')")
    @PostMapping("/check")
    public AjaxResult check(@Validated @RequestBody AbrQuestionnaireBody body)
    {
        return success(questionnaireService.checkAbrQuestionnaire(body.getDefinition()));
    }

    /**
     * 导入问卷版本（保存为草稿）
     */
    @PreAuthorize("@ss.hasPermi('abroad:questionnaire:import')")
    @Log(title = "问卷版本", businessType = BusinessType.IMPORT)
    @PostMapping
    public AjaxResult importQuestionnaire(@Validated @RequestBody AbrQuestionnaireBody body)
    {
        return success(questionnaireService.importAbrQuestionnaire(body.getDefinition(), body.getRemark(), getUsername()));
    }

    /**
     * 启用问卷版本
     */
    @PreAuthorize("@ss.hasPermi('abroad:questionnaire:activate')")
    @Log(title = "问卷版本启用", businessType = BusinessType.UPDATE)
    @PutMapping("/activate/{questionnaireId}")
    public AjaxResult activate(@PathVariable Long questionnaireId)
    {
        return toAjax(questionnaireService.activateAbrQuestionnaire(questionnaireId, getUsername()));
    }

    /**
     * 删除问卷草稿
     */
    @PreAuthorize("@ss.hasPermi('abroad:questionnaire:remove')")
    @Log(title = "问卷版本", businessType = BusinessType.DELETE)
    @DeleteMapping("/{questionnaireIds}")
    public AjaxResult remove(@PathVariable Long[] questionnaireIds)
    {
        return toAjax(questionnaireService.deleteAbrQuestionnaireByIds(questionnaireIds, getUsername()));
    }
}
