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
import com.ygb.abroad.domain.AbrComplianceRule;
import com.ygb.abroad.domain.AbrContent;
import com.ygb.abroad.service.IAbrComplianceService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 合规闸门 compliance_checker：合规词表、试检查、检查内容
 *
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/compliance")
public class AbrComplianceController extends BaseController
{
    @Autowired
    private IAbrComplianceService complianceService;

    /**
     * 查询合规词表（params[platform] 按平台筛选：含全平台规则）
     */
    @PreAuthorize("@ss.hasPermi('abroad:compliance:list')")
    @GetMapping("/rule/list")
    public TableDataInfo list(AbrComplianceRule rule)
    {
        startPage();
        List<AbrComplianceRule> list = complianceService.selectAbrComplianceRuleList(rule);
        return getDataTable(list);
    }

    /**
     * 获取规则详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:compliance:query')")
    @GetMapping("/rule/{ruleId}")
    public AjaxResult getInfo(@PathVariable Long ruleId)
    {
        return success(complianceService.selectAbrComplianceRuleById(ruleId));
    }

    /**
     * 新增规则
     */
    @PreAuthorize("@ss.hasPermi('abroad:compliance:add')")
    @Log(title = "合规词表", businessType = BusinessType.INSERT)
    @PostMapping("/rule")
    public AjaxResult add(@Validated @RequestBody AbrComplianceRule rule)
    {
        if (!complianceService.checkRuleUnique(rule))
        {
            return error("新增规则'" + rule.getPattern() + "'失败，同类别已有相同内容");
        }
        rule.setCreateBy(getUsername());
        return toAjax(complianceService.insertAbrComplianceRule(rule));
    }

    /**
     * 修改规则
     */
    @PreAuthorize("@ss.hasPermi('abroad:compliance:edit')")
    @Log(title = "合规词表", businessType = BusinessType.UPDATE)
    @PutMapping("/rule")
    public AjaxResult edit(@Validated @RequestBody AbrComplianceRule rule)
    {
        if (!complianceService.checkRuleUnique(rule))
        {
            return error("修改规则'" + rule.getPattern() + "'失败，同类别已有相同内容");
        }
        rule.setUpdateBy(getUsername());
        return toAjax(complianceService.updateAbrComplianceRule(rule));
    }

    /**
     * 删除规则
     */
    @PreAuthorize("@ss.hasPermi('abroad:compliance:remove')")
    @Log(title = "合规词表", businessType = BusinessType.DELETE)
    @DeleteMapping("/rule/{ruleIds}")
    public AjaxResult remove(@PathVariable Long[] ruleIds)
    {
        return toAjax(complianceService.deleteAbrComplianceRuleByIds(ruleIds));
    }

    /**
     * 试检查一条稿件（不保存；字段：platform、title、body、hashtags、citations、aiGenerated、contentKind）
     */
    @PreAuthorize("@ss.hasPermi('abroad:compliance:test')")
    @PostMapping("/test")
    public AjaxResult test(@RequestBody AbrContent content)
    {
        return success(complianceService.checkDraft(content).toMap());
    }

    /**
     * 立即检查一条内容并写入闸门结果
     */
    @PreAuthorize("@ss.hasPermi('abroad:compliance:check')")
    @Log(title = "合规闸门", businessType = BusinessType.OTHER)
    @PostMapping("/check/{contentId}")
    public AjaxResult check(@PathVariable Long contentId)
    {
        return success(complianceService.checkContent(contentId, getUsername()));
    }
}
