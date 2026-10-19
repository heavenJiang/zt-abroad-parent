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
import com.ygb.abroad.domain.AbrAiEvalCase;
import com.ygb.abroad.service.IAbrEvalCaseService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * AI 管理-评测集（数据库样例，与提示词目录下的 eval/*.json 一起参与评测命令）
 *
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/evalCase")
public class AbrEvalCaseController extends BaseController
{
    @Autowired
    private IAbrEvalCaseService evalCaseService;

    /**
     * 查询样例列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:evalCase:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrAiEvalCase evalCase)
    {
        startPage();
        List<AbrAiEvalCase> list = evalCaseService.selectAbrAiEvalCaseList(evalCase);
        return getDataTable(list);
    }

    /**
     * 获取样例详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:evalCase:query')")
    @GetMapping("/{caseId}")
    public AjaxResult getInfo(@PathVariable Long caseId)
    {
        return success(evalCaseService.selectAbrAiEvalCaseById(caseId));
    }

    /**
     * 新增样例
     */
    @PreAuthorize("@ss.hasPermi('abroad:evalCase:add')")
    @Log(title = "评测集", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody AbrAiEvalCase evalCase)
    {
        evalCase.setCreateBy(getUsername());
        return toAjax(evalCaseService.insertAbrAiEvalCase(evalCase));
    }

    /**
     * 修改样例（补全模型输入与断言后可启用）
     */
    @PreAuthorize("@ss.hasPermi('abroad:evalCase:edit')")
    @Log(title = "评测集", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody AbrAiEvalCase evalCase)
    {
        evalCase.setUpdateBy(getUsername());
        return toAjax(evalCaseService.updateAbrAiEvalCase(evalCase));
    }

    /**
     * 删除样例
     */
    @PreAuthorize("@ss.hasPermi('abroad:evalCase:remove')")
    @Log(title = "评测集", businessType = BusinessType.DELETE)
    @DeleteMapping("/{caseIds}")
    public AjaxResult remove(@PathVariable Long[] caseIds)
    {
        return toAjax(evalCaseService.deleteAbrAiEvalCaseByIds(caseIds));
    }
}
