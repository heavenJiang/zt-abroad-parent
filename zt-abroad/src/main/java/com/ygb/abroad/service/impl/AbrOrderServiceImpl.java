package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrLead;
import com.ygb.abroad.domain.AbrOrder;
import com.ygb.abroad.domain.AbrProduct;
import com.ygb.abroad.mapper.AbrOrderMapper;
import com.ygb.abroad.mapper.AbrProductMapper;
import com.ygb.abroad.service.IAbrEventService;
import com.ygb.abroad.service.IAbrLeadService;
import com.ygb.abroad.service.IAbrOrderService;
import com.ygb.abroad.service.IAbrProductService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DateUtils;
import com.ygb.common.utils.StringUtils;

/**
 * 订单 服务层实现
 *
 * @author ygb
 */
@Service
public class AbrOrderServiceImpl implements IAbrOrderService
{
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 金额上限（元） */
    static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");

    @Autowired
    private AbrOrderMapper orderMapper;

    @Autowired
    private IAbrProductService productService;

    @Autowired
    private AbrProductMapper productMapper;

    @Autowired
    private IAbrLeadService leadService;

    @Autowired
    private IAbrEventService eventService;

    @Override
    public List<AbrOrder> selectAbrOrderList(AbrOrder order)
    {
        return orderMapper.selectAbrOrderList(order);
    }

    @Override
    public AbrOrder selectAbrOrderById(Long orderId)
    {
        return orderMapper.selectAbrOrderById(orderId);
    }

    @Override
    public AbrOrder createOrder(Long leadId, String productCode, BigDecimal amount, String remark, String operator)
    {
        AbrLead lead = leadService.selectAbrLeadById(leadId);
        if (lead == null)
        {
            throw new ServiceException("线索不存在");
        }
        AbrProduct p = productService.requireActive(productCode);
        if (amount == null || amount.signum() < 0 || amount.compareTo(MAX_AMOUNT) > 0 || amount.scale() > 2)
        {
            throw new ServiceException("请填写 0 及以上、最多两位小数的金额");
        }
        String note = StringUtils.trimToEmpty(remark);
        boolean outside = (p.getPriceMin() != null && amount.compareTo(p.getPriceMin()) < 0) || (p.getPriceMax() != null && amount.compareTo(p.getPriceMax()) > 0);
        if (outside)
        {
            note = (note.isEmpty() ? "" : note + "；") + "金额不在产品价格区间（" + AbrBookingRules.priceText(p.getPriceMin(), p.getPriceMax(), p.getCurrency(), null) + "）";
        }
        AbrOrder o = new AbrOrder();
        o.setLeadId(lead.getLeadId());
        o.setProductCode(p.getProductCode());
        o.setProductName(p.getProductName());
        o.setAmount(amount);
        o.setCurrency(p.getCurrency());
        o.setCreateBy(operator);
        o.setRemark(StringUtils.substring(note, 0, 500));
        for (int i = 0; i < 5; i++)
        {
            o.setOrderNo("O" + DateUtils.dateTimeNow("yyyyMMdd") + String.format("%06d", RANDOM.nextInt(1_000_000)));
            try
            {
                orderMapper.insertAbrOrder(o);
                return orderMapper.selectAbrOrderById(o.getOrderId());
            }
            catch (DuplicateKeyException e)
            {
                // 订单号重复（极少见）：换一个
            }
        }
        throw new ServiceException("生成订单号失败，请重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AbrOrder confirmPaid(Long orderId, String payMethod, String payNote, String operator)
    {
        AbrOrder o = require(orderId);
        String method = StringUtils.trimToEmpty(payMethod);
        if (!AbrPlaybookServiceImpl.dictValues("abr_pay_method").contains(method))
        {
            throw new ServiceException("收款方式“" + method + "”不是可选值");
        }
        String note = StringUtils.trimToEmpty(payNote);
        if (note.length() > 200)
        {
            throw new ServiceException("收款说明最多 200 字");
        }
        if (note.matches(".*\\d{12,}.*"))
        {
            throw new ServiceException("收款说明不要写完整的银行卡号或账号，写流水尾号即可");
        }
        if (orderMapper.confirmPaid(o.getOrderId(), method, note, operator) == 0)
        {
            throw new ServiceException("只有待确认收款的订单可以确认收款");
        }
        AbrProduct p = productMapper.selectAbrProductByCode(o.getProductCode());
        AbrLead lead = leadService.selectAbrLeadById(o.getLeadId());
        if (p != null && AbrProduct.CATEGORY_SERVICE.equals(p.getCategory()) && lead != null && !"won".equals(lead.getStage()))
        {
            leadService.changeStage(lead.getLeadId(), "won", null, operator);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("order_id", o.getOrderId());
        payload.put("lead_id", o.getLeadId());
        payload.put("product_code", o.getProductCode());
        eventService.recordSystemEvent(EVENT_PAID, "order:" + o.getOrderId(), payload);
        return orderMapper.selectAbrOrderById(o.getOrderId());
    }

    @Override
    public AbrOrder cancelOrRefund(Long orderId, boolean refund, String reason, String operator)
    {
        AbrOrder o = require(orderId);
        String text = StringUtils.trimToEmpty(reason);
        if (text.isEmpty() || text.length() > 500)
        {
            throw new ServiceException("请填写原因（最多 500 字）");
        }
        String from = refund ? AbrOrder.STATUS_PAID : AbrOrder.STATUS_PENDING;
        String to = refund ? AbrOrder.STATUS_REFUNDED : AbrOrder.STATUS_CANCELLED;
        if (orderMapper.changeStatus(o.getOrderId(), from, to, text, operator) == 0)
        {
            throw new ServiceException(refund ? "只有已确认收款的订单可以退款" : "只有待确认收款的订单可以取消");
        }
        return orderMapper.selectAbrOrderById(o.getOrderId());
    }

    private AbrOrder require(Long orderId)
    {
        AbrOrder o = orderId == null ? null : orderMapper.selectAbrOrderById(orderId);
        if (o == null)
        {
            throw new ServiceException("订单不存在");
        }
        return o;
    }
}
