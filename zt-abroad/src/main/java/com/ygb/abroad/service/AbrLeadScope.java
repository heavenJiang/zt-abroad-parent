package com.ygb.abroad.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ygb.abroad.domain.AbrBooking;
import com.ygb.abroad.domain.AbrLead;
import com.ygb.abroad.domain.AbrOrder;
import com.ygb.abroad.mapper.AbrBookingMapper;
import com.ygb.abroad.mapper.AbrLeadMapper;
import com.ygb.abroad.mapper.AbrOrderMapper;
import com.ygb.common.utils.SecurityUtils;

/**
 * 线索数据范围：有“查看全部线索”权限（abroad:lead:all，负责人）的员工看全部，其余（顾问）只能看、改分配给自己的线索及其预约、订单。
 * 供控制器 {@code @PreAuthorize("... and @abrLeadScope.lead(#p0)")} 与列表过滤使用；记录不存在时放行，由业务方法报“不存在”。
 *
 * @author ygb
 */
@Component("abrLeadScope")
public class AbrLeadScope
{
    /** 查看全部线索 */
    public static final String PERM_ALL = "abroad:lead:all";

    @Autowired
    private AbrLeadMapper leadMapper;

    @Autowired
    private AbrBookingMapper bookingMapper;

    @Autowired
    private AbrOrderMapper orderMapper;

    /**
     * 当前员工是否可看全部线索
     *
     * @return 是否
     */
    public boolean all()
    {
        return SecurityUtils.hasPermi(PERM_ALL);
    }

    /**
     * 列表过滤用的负责人：看全部时为 null，否则为当前员工
     *
     * @return 员工ID
     */
    public Long scopeUserId()
    {
        return all() ? null : SecurityUtils.getUserId();
    }

    /**
     * 能否访问线索（合并过的线索按合并后的主线索判断）
     *
     * @param leadId 线索ID
     * @return 是否
     */
    public boolean lead(Long leadId)
    {
        if (leadId == null || all())
        {
            return true;
        }
        AbrLead l = leadMapper.selectAbrLeadById(leadId);
        for (int i = 0; l != null && l.getMergedInto() != null && i < 10; i++)
        {
            l = leadMapper.selectAbrLeadById(l.getMergedInto());
        }
        return l == null || SecurityUtils.getUserId().equals(l.getOwnerUserId());
    }

    /**
     * 能否访问请求体中的线索（body.leadId）
     *
     * @param body 请求体
     * @return 是否
     */
    public boolean leadOf(Map<String, Object> body)
    {
        Object id = body == null ? null : body.get("leadId");
        return !(id instanceof Number) || lead(((Number) id).longValue());
    }

    /**
     * 能否访问预约：线索归自己，或自己是这次预约的顾问
     *
     * @param bookingId 预约ID
     * @return 是否
     */
    public boolean booking(Long bookingId)
    {
        if (bookingId == null || all())
        {
            return true;
        }
        AbrBooking b = bookingMapper.selectAbrBookingById(bookingId);
        return b == null || SecurityUtils.getUserId().equals(b.getConsultantId()) || (b.getLeadId() != null && lead(b.getLeadId()));
    }

    /**
     * 能否访问订单：线索归自己
     *
     * @param orderId 订单ID
     * @return 是否
     */
    public boolean order(Long orderId)
    {
        if (orderId == null || all())
        {
            return true;
        }
        AbrOrder o = orderMapper.selectAbrOrderById(orderId);
        return o == null || o.getLeadId() == null || lead(o.getLeadId());
    }
}
