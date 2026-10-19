package com.ygb.web.controller.app;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.vo.AppBookingBody;
import com.ygb.abroad.service.IAbrBookingService;
import com.ygb.abroad.service.IAbrProductService;
import com.ygb.common.annotation.Anonymous;
import com.ygb.common.annotation.RateLimiter;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.enums.LimitType;

/**
 * C 端-产品与预约（查看产品与可约时段匿名可用；预约、改约、取消须登录，本人取学生令牌）
 *
 * @author ygb
 */
@Anonymous
@RestController
@RequestMapping("/app/booking")
public class AppBookingController extends BaseController
{
    @Autowired
    private IAbrProductService productService;

    @Autowired
    private IAbrBookingService bookingService;

    /**
     * 产品（bookable=true 只列可在线预约的）
     */
    @RateLimiter(key = "app_booking_products:", time = 60, count = 60, limitType = LimitType.IP)
    @GetMapping("/products")
    public AjaxResult products(@RequestParam(defaultValue = "false") boolean bookable)
    {
        return success(productService.appProducts(bookable));
    }

    /**
     * 可约时段
     */
    @RateLimiter(key = "app_booking_slots:", time = 60, count = 60, limitType = LimitType.IP)
    @GetMapping("/slots")
    public AjaxResult slots(@RequestParam String productCode, @RequestParam(required = false) String from, @RequestParam(defaultValue = "7") int days)
    {
        return success(bookingService.appSlots(productCode, from, days));
    }

    /**
     * 预约（productCode、slotId、note）
     */
    @RateLimiter(key = "app_booking_book:", time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping
    public AjaxResult book(@RequestBody AppBookingBody body)
    {
        return success(bookingService.appBook(body));
    }

    /**
     * 我的预约
     */
    @RateLimiter(key = "app_booking_mine:", time = 60, count = 60, limitType = LimitType.IP)
    @GetMapping("/mine")
    public AjaxResult mine()
    {
        return success(bookingService.appMine());
    }

    /**
     * 改约（slotId）
     */
    @RateLimiter(key = "app_booking_reschedule:", time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping("/reschedule/{bookingId}")
    public AjaxResult reschedule(@PathVariable Long bookingId, @RequestBody AppBookingBody body)
    {
        return success(bookingService.appReschedule(bookingId, body.getSlotId()));
    }

    /**
     * 取消（reason 可空）
     */
    @RateLimiter(key = "app_booking_cancel:", time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping("/cancel/{bookingId}")
    public AjaxResult cancel(@PathVariable Long bookingId, @RequestBody(required = false) AppBookingBody body)
    {
        return success(bookingService.appCancel(bookingId, body == null ? null : body.getReason()));
    }
}
