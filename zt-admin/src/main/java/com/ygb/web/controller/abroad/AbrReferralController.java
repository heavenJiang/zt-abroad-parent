package com.ygb.web.controller.abroad;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrReferral;
import com.ygb.abroad.domain.AbrReferralRule;
import com.ygb.abroad.domain.AbrReferrer;
import com.ygb.abroad.domain.AbrReward;
import com.ygb.abroad.service.IAbrReferralService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 获客运营-推荐与奖励（推荐人与推荐码、被推荐人、奖励规则、奖励发放）
 *
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/referral")
public class AbrReferralController extends BaseController
{
    @Autowired
    private IAbrReferralService referralService;

    /**
     * 查询推荐人（带推荐链接与人数）
     */
    @PreAuthorize("@ss.hasPermi('abroad:referral:list')")
    @GetMapping("/referrer/list")
    public TableDataInfo referrers(AbrReferrer referrer)
    {
        startPage();
        List<AbrReferrer> list = referralService.selectReferrerList(referrer);
        list.forEach(f -> f.getParams().put("link", referralService.link(f.getRefCode())));
        return getDataTable(list);
    }

    /**
     * 新增推荐人（生成推荐码）
     */
    @PreAuthorize("@ss.hasPermi('abroad:referral:add')")
    @Log(title = "推荐人", businessType = BusinessType.INSERT)
    @PostMapping("/referrer")
    public AjaxResult add(@RequestBody AbrReferrer referrer)
    {
        referrer.setReferrerId(null);
        referrer.setCreateBy(getUsername());
        return success(referralService.addReferrer(referrer));
    }

    /**
     * 修改推荐人（推荐码不变）
     */
    @PreAuthorize("@ss.hasPermi('abroad:referral:edit')")
    @Log(title = "推荐人", businessType = BusinessType.UPDATE)
    @PutMapping("/referrer")
    public AjaxResult edit(@RequestBody AbrReferrer referrer)
    {
        referrer.setUpdateBy(getUsername());
        return toAjax(referralService.updateReferrer(referrer));
    }

    /**
     * 删除推荐人（有被推荐人的不能删）
     */
    @PreAuthorize("@ss.hasPermi('abroad:referral:remove')")
    @Log(title = "推荐人", businessType = BusinessType.DELETE)
    @DeleteMapping("/referrer/{referrerIds}")
    public AjaxResult remove(@PathVariable Long[] referrerIds)
    {
        return toAjax(referralService.deleteReferrers(referrerIds));
    }

    /**
     * 查询被推荐人（referrerId、status）
     */
    @PreAuthorize("@ss.hasPermi('abroad:referral:list')")
    @GetMapping("/list")
    public TableDataInfo referrals(AbrReferral referral)
    {
        startPage();
        List<AbrReferral> list = referralService.selectReferralList(referral);
        return getDataTable(list);
    }

    /**
     * 奖励规则
     */
    @PreAuthorize("@ss.hasPermi('abroad:referral:list')")
    @GetMapping("/rule/list")
    public AjaxResult rules()
    {
        return success(referralService.selectRuleList());
    }

    /**
     * 修改奖励规则（只发服务抵扣或积分，不发现金）
     */
    @PreAuthorize("@ss.hasPermi('abroad:referral:edit')")
    @Log(title = "推荐奖励规则", businessType = BusinessType.UPDATE)
    @PutMapping("/rule")
    public AjaxResult editRule(@RequestBody AbrReferralRule rule)
    {
        rule.setUpdateBy(getUsername());
        return toAjax(referralService.updateRule(rule));
    }

    /**
     * 查询奖励（rewardType、status、appUserId、leadId、referrerId）
     */
    @PreAuthorize("@ss.hasPermi('abroad:referral:list')")
    @GetMapping("/reward/list")
    public TableDataInfo rewards(AbrReward reward)
    {
        startPage();
        List<AbrReward> list = referralService.selectRewardList(reward);
        return getDataTable(list);
    }

    /**
     * 奖励确认发放（confirm）、标记已使用（use）、作废（void）
     */
    @PreAuthorize("@ss.hasPermi('abroad:reward:confirm')")
    @Log(title = "奖励发放", businessType = BusinessType.UPDATE)
    @PutMapping("/reward/{action}/{rewardId}")
    public AjaxResult changeReward(@PathVariable String action, @PathVariable Long rewardId)
    {
        return toAjax(referralService.changeReward(rewardId, action, getUsername()));
    }
}
