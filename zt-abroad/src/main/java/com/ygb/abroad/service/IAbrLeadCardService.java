package com.ygb.abroad.service;

import com.ygb.abroad.domain.AbrLeadCard;

/**
 * 线索卡片 lead_card 服务层（仅内部可见：基础信息由规则取数，背景摘要、顾虑、开场话题经 AI 网关生成，失败时只给基础信息）
 *
 * @author ygb
 */
public interface IAbrLeadCardService
{
    /** Agent 编码 */
    public static final String AGENT = "lead_card";

    /**
     * 生成或刷新卡片（模型输入未变化且已有 AI 内容时不重复调用模型，只刷新基础信息）
     *
     * @param leadId 线索ID
     * @param trigger 触发（grade / manual / booking）
     * @param force 是否强制重新调用模型
     * @param operator 操作人
     * @return 卡片
     */
    public AbrLeadCard generate(Long leadId, String trigger, boolean force, String operator);

    /**
     * 线索的卡片
     *
     * @param leadId 线索ID
     * @return 卡片，没有为 null
     */
    public AbrLeadCard selectByLead(Long leadId);
}
