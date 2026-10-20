package com.ygb.abroad.service;

import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrPartnerCommission;

/**
 * 合作方分成 服务层（M9-05）
 * 
 * @author ygb
 */
public interface IAbrPartnerService
{
    /**
     * 合作方可嵌入的工具链接（C 端工具页 + ch=partner + ct=推荐码）
     * 
     * @param referrerId 合作方
     * @return 每个工具一行：tool、label、url
     */
    public List<Map<String, Object>> links(Long referrerId);

    /**
     * 工具链接的二维码
     * 
     * @param referrerId 合作方
     * @param tool 工具编码
     * @return PNG
     */
    public byte[] qr(Long referrerId, String tool);

    /**
     * 对账：补算新收款订单的分成；订单取消或退款时未发放的作废、已发放的标记待追回
     * 
     * @param operator 操作人
     * @return created、voided、flagged
     */
    public Map<String, Object> reconcile(String operator);

    /**
     * 查询分成列表
     * 
     * @param commission 条件
     * @return 分成集合
     */
    public List<AbrPartnerCommission> selectCommissionList(AbrPartnerCommission commission);

    /**
     * 导出用的分成列表（学生称呼只保留第一个字，可直接发给合作方核对）
     * 
     * @param commission 条件
     * @return 分成集合
     */
    public List<AbrPartnerCommission> selectCommissionExport(AbrPartnerCommission commission);

    /**
     * 月度对账单
     * 
     * @param month 结算月份 yyyy-MM（不填为本月）
     * @return month、rows（每个合作方每个币种一行）
     */
    public Map<String, Object> statement(String month);

    /**
     * 确认分成（只处理待确认的）
     * 
     * @return 确认条数
     */
    public int confirm(Long[] ids, String operator);

    /**
     * 登记已发放（只处理已确认的）
     * 
     * @param note 发放说明（转账流水号等）
     * @return 登记条数
     */
    public int pay(Long[] ids, String note, String operator);

    /**
     * 作废（待确认或已确认的）
     * 
     * @return 结果
     */
    public int voidCommission(Long id, String reason, String operator);

    /**
     * 已发放后订单退款的追回处理完毕
     * 
     * @return 结果
     */
    public int clearRefund(Long id, String note, String operator);
}
