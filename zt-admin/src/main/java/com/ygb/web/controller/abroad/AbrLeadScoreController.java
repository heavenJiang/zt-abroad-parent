package com.ygb.web.controller.abroad;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrLeadScoreRule;
import com.ygb.abroad.service.IAbrLeadScoreService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.enums.BusinessType;

/**
 * 获客运营-线索评分规则（规则种类固定，分值与阈值可改；修改后下一次评分生效，每天 06:00 全部重算）
 *
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/leadScore")
public class AbrLeadScoreController extends BaseController
{
    @Autowired
    private IAbrLeadScoreService scoreService;

    /**
     * 评分规则列表
     */
    @PreAuthorize("@ss.hasPermi('abroad:leadScore:list')")
    @GetMapping("/rule/list")
    public AjaxResult list()
    {
        return success(scoreService.selectRules());
    }

    /**
     * 修改评分规则（ruleParams 为 JSON 对象，键固定；status 0 正常 / 1 停用）
     */
    @PreAuthorize("@ss.hasPermi('abroad:leadScore:edit')")
    @Log(title = "线索评分规则", businessType = BusinessType.UPDATE)
    @PutMapping("/rule")
    public AjaxResult edit(@Validated @RequestBody AbrLeadScoreRule rule)
    {
        rule.setUpdateBy(getUsername());
        return toAjax(scoreService.updateRule(rule));
    }
}
