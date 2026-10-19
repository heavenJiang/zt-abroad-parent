package com.ygb.web.controller.abroad;

import java.util.List;
import java.util.Map;
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
import com.ygb.abroad.domain.AbrBooking;
import com.ygb.abroad.domain.AbrConsultSlot;
import com.ygb.abroad.domain.vo.AbrSlotBatchBody;
import com.ygb.abroad.service.IAbrBookingService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;

/**
 * 获客运营-预约与顾问时段
 *
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/booking")
public class AbrBookingController extends BaseController
{
    @Autowired
    private IAbrBookingService bookingService;

    /**
     * 查询预约（leadId、consultantId、status、productCode；params[beginTime] / params[endTime] 开始日期）
     */
    @PreAuthorize("@ss.hasPermi('abroad:booking:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrBooking booking)
    {
        startPage();
        List<AbrBooking> list = bookingService.selectBookings(booking);
        return getDataTable(list);
    }

    /**
     * 预约详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:booking:list')")
    @GetMapping("/{bookingId}")
    public AjaxResult getInfo(@PathVariable Long bookingId)
    {
        return success(bookingService.selectBooking(bookingId));
    }

    /**
     * 员工代约（leadId、productCode、slotId、note）
     */
    @PreAuthorize("@ss.hasPermi('abroad:booking:add')")
    @Log(title = "预约", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody Map<String, Object> body)
    {
        return success(bookingService.bookForLead(num(body.get("leadId")), str(body.get("productCode")), num(body.get("slotId")), str(body.get("note")),
                getUsername()));
    }

    /**
     * 改约（slotId）
     */
    @PreAuthorize("@ss.hasPermi('abroad:booking:edit')")
    @Log(title = "预约", businessType = BusinessType.UPDATE)
    @PutMapping("/reschedule/{bookingId}")
    public AjaxResult reschedule(@PathVariable Long bookingId, @RequestBody Map<String, Object> body)
    {
        return success(bookingService.reschedule(bookingId, num(body.get("slotId")), getUsername()));
    }

    /**
     * 取消（reason 必填）
     */
    @PreAuthorize("@ss.hasPermi('abroad:booking:edit')")
    @Log(title = "预约", businessType = BusinessType.UPDATE)
    @PutMapping("/cancel/{bookingId}")
    public AjaxResult cancel(@PathVariable Long bookingId, @RequestBody Map<String, Object> body)
    {
        return success(bookingService.cancel(bookingId, str(body.get("reason")), getUsername()));
    }

    /**
     * 标记已完成（done）或未到（no_show）
     */
    @PreAuthorize("@ss.hasPermi('abroad:booking:edit')")
    @Log(title = "预约", businessType = BusinessType.UPDATE)
    @PutMapping("/finish/{bookingId}/{status}")
    public AjaxResult finish(@PathVariable Long bookingId, @PathVariable String status)
    {
        return success(bookingService.finish(bookingId, status, getUsername()));
    }

    /**
     * 查询顾问时段（userId、status；params[beginTime] / params[endTime]）
     */
    @PreAuthorize("@ss.hasPermi('abroad:booking:slot')")
    @GetMapping("/slot/list")
    public TableDataInfo slots(AbrConsultSlot slot)
    {
        startPage();
        List<AbrConsultSlot> list = bookingService.selectSlots(slot);
        return getDataTable(list);
    }

    /**
     * 批量开放时段
     */
    @PreAuthorize("@ss.hasPermi('abroad:booking:slot')")
    @Log(title = "顾问时段", businessType = BusinessType.INSERT)
    @PostMapping("/slot/batch")
    public AjaxResult batch(@RequestBody AbrSlotBatchBody body)
    {
        return success(bookingService.batchSlots(body, getUsername()));
    }

    /**
     * 关闭可约时段
     */
    @PreAuthorize("@ss.hasPermi('abroad:booking:slot')")
    @Log(title = "顾问时段", businessType = BusinessType.UPDATE)
    @PutMapping("/slot/close/{slotIds}")
    public AjaxResult close(@PathVariable Long[] slotIds)
    {
        return toAjax(bookingService.closeSlots(slotIds, getUsername()));
    }

    /**
     * 删除没有被预约的时段
     */
    @PreAuthorize("@ss.hasPermi('abroad:booking:slot')")
    @Log(title = "顾问时段", businessType = BusinessType.DELETE)
    @DeleteMapping("/slot/{slotIds}")
    public AjaxResult removeSlots(@PathVariable Long[] slotIds)
    {
        return toAjax(bookingService.deleteSlots(slotIds));
    }

    private static Long num(Object o)
    {
        return o instanceof Number n ? n.longValue() : null;
    }

    private static String str(Object o)
    {
        return o == null ? null : String.valueOf(o);
    }
}
