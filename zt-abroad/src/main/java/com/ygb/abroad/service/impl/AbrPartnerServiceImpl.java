package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrPartnerCommission;
import com.ygb.abroad.domain.AbrReferrer;
import com.ygb.abroad.growth.card.QrCodes;
import com.ygb.abroad.mapper.AbrPartnerMapper;
import com.ygb.abroad.mapper.AbrReferralMapper;
import com.ygb.abroad.service.IAbrPartnerService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;

/**
 * 合作方分成 服务层实现：分成在每天对账时按订单生成（不预付），确认与发放由员工操作
 * 
 * @author ygb
 */
@Service
public class AbrPartnerServiceImpl implements IAbrPartnerService
{
    private static final Logger log = LoggerFactory.getLogger(AbrPartnerServiceImpl.class);

    /** 二维码边长 */
    private static final int QR_SIZE = 480;

    @Autowired
    private AbrPartnerMapper partnerMapper;

    @Autowired
    private AbrReferralMapper referralMapper;

    @Autowired
    private AbrShareSupport shareSupport;

    /** 合作方没设比例时的默认分成比例（%；0 表示须在合作方上设置才分成） */
    @Value("${abroad.partner.default-rate:0}")
    private BigDecimal defaultRate;

    /** 合作方没设时的归因有效天数 */
    @Value("${abroad.partner.window-days:365}")
    private int windowDays;

    /** 可嵌入的工具（编码:名称:C 端页面，逗号分隔） */
    @Value("${abroad.partner.tools:home:首页:}")
    private String toolsConfig;

    @Override
    public List<Map<String, Object>> links(Long referrerId)
    {
        AbrReferrer f = partner(referrerId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (AbrPartnerRules.Tool t : AbrPartnerRules.tools(toolsConfig))
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tool", t.code());
            row.put("label", t.label());
            row.put("url", url(t, f));
            out.add(row);
        }
        return out;
    }

    @Override
    public byte[] qr(Long referrerId, String tool)
    {
        AbrReferrer f = partner(referrerId);
        AbrPartnerRules.Tool t = AbrPartnerRules.tools(toolsConfig).stream().filter(x -> x.code().equals(tool)).findFirst()
                .orElseThrow(() -> new ServiceException("没有这个工具"));
        return QrCodes.png(url(t, f), QR_SIZE);
    }

    private String url(AbrPartnerRules.Tool t, AbrReferrer f)
    {
        return shareSupport.appLink(AbrPartnerRules.pathAndQuery(t, AbrReferralRules.PARTNER_CHANNEL, f.getRefCode()));
    }

    private AbrReferrer partner(Long referrerId)
    {
        AbrReferrer f = referrerId == null ? null : referralMapper.selectReferrerById(referrerId);
        if (f == null || !AbrReferralRules.PARTNER.equals(f.getReferrerType()))
        {
            throw new ServiceException("合作方不存在");
        }
        return f;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reconcile(String operator)
    {
        BigDecimal rate = defaultRate == null || defaultRate.signum() < 0 ? BigDecimal.ZERO : defaultRate;
        int created = partnerMapper.insertCommissions(rate, Math.max(1, windowDays), operator);
        int voided = partnerMapper.voidCancelledOrders(operator);
        int flagged = partnerMapper.flagRefundedPaid(operator);
        if (created + voided + flagged > 0)
        {
            log.info("合作方分成对账：新增 {}，作废 {}，待追回 {}", created, voided, flagged);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("created", created);
        out.put("voided", voided);
        out.put("flagged", flagged);
        return out;
    }

    @Override
    public List<AbrPartnerCommission> selectCommissionList(AbrPartnerCommission commission)
    {
        return partnerMapper.selectCommissionList(commission);
    }

    @Override
    public List<AbrPartnerCommission> selectCommissionExport(AbrPartnerCommission commission)
    {
        List<AbrPartnerCommission> list = partnerMapper.selectCommissionList(commission);
        list.forEach(c -> c.setLeadName(AbrPartnerRules.maskName(c.getLeadName())));
        return list;
    }

    @Override
    public Map<String, Object> statement(String month)
    {
        String m = AbrPartnerRules.month(month, LocalDate.now(AbrLookupTools.BEIJING));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("month", m);
        out.put("rows", partnerMapper.selectStatement(m));
        return out;
    }

    @Override
    public int confirm(Long[] ids, String operator)
    {
        requireIds(ids);
        return partnerMapper.confirmCommissions(ids, operator);
    }

    @Override
    public int pay(Long[] ids, String note, String operator)
    {
        requireIds(ids);
        return partnerMapper.payCommissions(ids, AbrPartnerRules.note(note, true, "发放说明"), operator);
    }

    @Override
    public int voidCommission(Long id, String reason, String operator)
    {
        int rows = partnerMapper.voidCommission(id, AbrPartnerRules.note(reason, true, "作废原因"), operator);
        if (rows == 0)
        {
            throw new ServiceException("只有待确认或已确认的分成可以作废");
        }
        return rows;
    }

    @Override
    public int clearRefund(Long id, String note, String operator)
    {
        int rows = partnerMapper.clearRefundFlag(id, AbrPartnerRules.note(note, true, "处理说明"), operator);
        if (rows == 0)
        {
            throw new ServiceException("这条分成没有待追回的退款");
        }
        return rows;
    }

    private static void requireIds(Long[] ids)
    {
        if (ids == null || ids.length == 0 || ids.length > 200)
        {
            throw new ServiceException("请选择 1–200 条分成");
        }
    }
}
