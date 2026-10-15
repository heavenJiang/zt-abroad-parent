package com.ygb.web.controller.abroad;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrMomentRule;
import com.ygb.abroad.service.IAbrMomentRuleService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 测评与选校-转化时刻规则（时刻 → 推荐的服务、文案与阈值；时刻种类固定，只能修改不能增删）
 * 
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/momentRule")
public class AbrMomentRuleController extends BaseController
{
    @Autowired
    private IAbrMomentRuleService ruleService;

    /**
     * 查询规则列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:momentRule:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrMomentRule rule)
    {
        startPage();
        List<AbrMomentRule> list = ruleService.selectAbrMomentRuleList(rule);
        return getDataTable(list);
    }

    /**
     * 获取规则详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:momentRule:query')")
    @GetMapping(value = "/{ruleId}")
    public AjaxResult getInfo(@PathVariable Long ruleId)
    {
        return success(ruleService.selectAbrMomentRuleById(ruleId));
    }

    /**
     * 修改规则
     */
    @PreAuthorize("@ss.hasPermi('abroad:momentRule:edit')")
    @Log(title = "转化时刻规则", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody AbrMomentRule rule)
    {
        rule.setUpdateBy(getUsername());
        return toAjax(ruleService.updateAbrMomentRule(rule));
    }
}
