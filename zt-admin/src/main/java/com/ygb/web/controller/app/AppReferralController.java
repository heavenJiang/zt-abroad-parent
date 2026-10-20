package com.ygb.web.controller.app;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.service.IAbrReferralService;
import com.ygb.common.annotation.Anonymous;
import com.ygb.common.annotation.RateLimiter;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.enums.LimitType;

/**
 * C 端-我的推荐码（须登录；推荐码由工作室在签约后生成，奖励在“我的回填与奖励”中查看）
 *
 * @author ygb
 */
@Anonymous
@RestController
@RequestMapping("/app/referral")
public class AppReferralController extends BaseController
{
    @Autowired
    private IAbrReferralService referralService;

    /**
     * 我的推荐码、链接与进度（已进入 / 有效测评 / 已签约人数，不含被推荐人的任何信息）
     */
    @RateLimiter(key = "app_referral_mine:", time = 60, count = 60, limitType = LimitType.IP)
    @GetMapping("/mine")
    public AjaxResult mine()
    {
        return success(referralService.appMine());
    }
}
