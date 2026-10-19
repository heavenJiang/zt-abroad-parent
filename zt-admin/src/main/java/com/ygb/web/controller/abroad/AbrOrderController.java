package com.ygb.web.controller.abroad;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrOrder;
import com.ygb.abroad.service.IAbrOrderService;
import com.ygb.common.annotation.Log;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.core.page.TableDataInfo;
import com.ygb.common.enums.BusinessType;
import com.ygb.common.exception.ServiceException;

/**
 * 获客运营-订单（人工确认收款）
 *
 * @author ygb
 */
@RestController
@RequestMapping("/abroad/order")
public class AbrOrderController extends BaseController
{
    @Autowired
    private IAbrOrderService orderService;

    /**
     * 查询订单
     */
    @PreAuthorize("@ss.hasPermi('abroad:order:list')")
    @GetMapping("/list")
    public TableDataInfo list(AbrOrder order)
    {
        startPage();
        List<AbrOrder> list = orderService.selectAbrOrderList(order);
        return getDataTable(list);
    }

    /**
     * 订单详情
     */
    @PreAuthorize("@ss.hasPermi('abroad:order:list')")
    @GetMapping("/{orderId}")
    public AjaxResult getInfo(@PathVariable Long orderId)
    {
        return success(orderService.selectAbrOrderById(orderId));
    }

    /**
     * 新建订单（leadId、productCode、amount、remark）
     */
    @PreAuthorize("@ss.hasPermi('abroad:order:add')")
    @Log(title = "订单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody Map<String, Object> body)
    {
        Object amount = body.get("amount");
        BigDecimal value;
        try
        {
            value = amount == null ? null : new BigDecimal(String.valueOf(amount));
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException("金额格式不对");
        }
        Object lead = body.get("leadId");
        return success(orderService.createOrder(lead instanceof Number n ? n.longValue() : null, (String) body.get("productCode"), value,
                (String) body.get("remark"), getUsername()));
    }

    /**
     * 确认收款（payMethod、payNote）
     */
    @PreAuthorize("@ss.hasPermi('abroad:order:confirm')")
    @Log(title = "订单", businessType = BusinessType.UPDATE)
    @PutMapping("/confirm/{orderId}")
    public AjaxResult confirm(@PathVariable Long orderId, @RequestBody Map<String, String> body)
    {
        return success(orderService.confirmPaid(orderId, body.get("payMethod"), body.get("payNote"), getUsername()));
    }

    /**
     * 取消待确认收款的订单（reason）
     */
    @PreAuthorize("@ss.hasPermi('abroad:order:edit')")
    @Log(title = "订单", businessType = BusinessType.UPDATE)
    @PutMapping("/cancel/{orderId}")
    public AjaxResult cancel(@PathVariable Long orderId, @RequestBody Map<String, String> body)
    {
        return success(orderService.cancelOrRefund(orderId, false, body.get("reason"), getUsername()));
    }

    /**
     * 已确认收款的订单退款（reason）
     */
    @PreAuthorize("@ss.hasPermi('abroad:order:edit')")
    @Log(title = "订单", businessType = BusinessType.UPDATE)
    @PutMapping("/refund/{orderId}")
    public AjaxResult refund(@PathVariable Long orderId, @RequestBody Map<String, String> body)
    {
        return success(orderService.cancelOrRefund(orderId, true, body.get("reason"), getUsername()));
    }
}
