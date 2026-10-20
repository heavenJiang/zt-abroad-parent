package com.ygb.web.controller.app;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.service.IAbrCaseService;
import com.ygb.common.annotation.Anonymous;
import com.ygb.common.annotation.RateLimiter;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.enums.LimitType;

/**
 * C 端-offer 墙
 *
 * 匿名接口：只返回已取得宣传授权、核验 A / B 的录取，背景信息已泛化（院校层次、均分区间），不含任何可识别个人的字段；按 IP 限流。
 *
 * @author ygb
 */
@Anonymous
@RestController
@RequestMapping("/app/offer")
public class AppOfferController extends BaseController
{
    @Autowired
    private IAbrCaseService caseService;

    /**
     * offer 墙（country、degree 可选）
     */
    @RateLimiter(key = "app_offer_wall:", time = 60, count = 30, limitType = LimitType.IP)
    @GetMapping("/wall")
    public AjaxResult wall(@RequestParam(required = false) String country, @RequestParam(required = false) String degree)
    {
        return success(caseService.publicOffers(country, degree));
    }

    /**
     * offer 卡（PNG；不可公开时当作不存在）
     */
    @RateLimiter(key = "app_offer_card:", time = 60, count = 20, limitType = LimitType.IP)
    @GetMapping("/card/{caseId}")
    public ResponseEntity<byte[]> card(@PathVariable Long caseId)
    {
        byte[] png = caseService.publicCardPng(caseId);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.maxAge(Duration.ofMinutes(10))).body(png);
    }
}
