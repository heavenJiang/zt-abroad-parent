package com.ygb.abroad.service;

import java.util.Map;
import java.util.Set;
import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppPlanVo;
import tools.jackson.databind.JsonNode;

/**
 * 业务事件 服务层：C 端写操作记事件（替代员工操作日志），转化时刻记录与推荐展示
 * 
 * @author ygb
 */
public interface IAbrEventService
{
    /**
     * 记录一条 C 端业务事件（写入失败不影响业务）
     * 
     * @param visitor 访客
     * @param eventType 事件类型
     * @param refType 关联对象类型（可为 null）
     * @param refId 关联对象ID（可为 null）
     * @param payload 事件内容（可为 null）
     */
    public void recordAction(AbrVisitor visitor, String eventType, String refType, Long refId, Map<String, Object> payload);

    /**
     * 启用的转化时刻规则的阈值参数
     * 
     * @return 时刻编码 → 阈值参数（停用的时刻不在其中）
     */
    public Map<String, JsonNode> activeMomentParams();

    /**
     * 访客已记录的转化时刻
     * 
     * @param visitorId 访客ID
     * @return 时刻编码
     */
    public Set<String> recordedMoments(Long visitorId);

    /**
     * 记录转化时刻（每类每人只记一次；规则停用时不记）；首次记录时发布 PlanMomentDetected
     * 
     * @param visitor 访客
     * @param moment 时刻编码
     * @param payload 事件内容
     * @return 是否为首次记录
     */
    public boolean recordMoment(AbrVisitor visitor, String moment, Map<String, Object> payload);

    /**
     * 取一条待展示的推荐并标为已展示（距上次展示不足频控间隔、或访客为未成年人时不展示）
     * 
     * @param visitor 访客
     * @return 推荐，没有返回 null
     */
    public AppPlanVo.Recommendation takeRecommendation(AbrVisitor visitor);
}
