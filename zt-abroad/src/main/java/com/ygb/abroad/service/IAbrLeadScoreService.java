package com.ygb.abroad.service;

import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrLead;
import com.ygb.abroad.domain.AbrLeadScoreRule;

/**
 * 线索评分 lead_scorer 服务层：按规则评分并给出理由，顾问可修改等级（须填原因），等级变化记历史并写事件
 *
 * @author ygb
 */
public interface IAbrLeadScoreService
{
    /** Agent 编码 */
    public static final String AGENT = "lead_scorer";

    /** 事件：等级变化（M5-03 分配与 SLA、M8-04 邀约订阅） */
    public static final String EVENT_GRADE_CHANGED = "lead_grade_changed";

    /**
     * 为一条线索评分（已合并的按保留的一条）
     *
     * @param leadId 线索ID
     * @param operator 操作人
     * @return 评分后的线索（手机号脱敏）
     */
    public AbrLead score(Long leadId, String operator);

    /**
     * 按访客或学生账号找到线索并评分（转化时刻、绑定手机号事件用）
     *
     * @param visitorId 访客ID
     * @param appUserId 学生账号ID
     * @param operator 操作人
     * @return 评分后的线索；还没有线索时为 null
     */
    public AbrLead scoreByVisitor(Long visitorId, Long appUserId, String operator);

    /**
     * 重算全部未签约、未流失的线索
     *
     * @param operator 操作人
     * @return 重算数、等级变化数
     */
    public Map<String, Object> rescoreAll(String operator);

    /**
     * 顾问修改等级（须填原因）；grade 为空时撤销修改、恢复规则等级
     *
     * @param leadId 线索ID
     * @param grade A / B / C，或空
     * @param reason 原因
     * @param operator 操作人
     * @return 修改后的线索
     */
    public AbrLead override(Long leadId, String grade, String reason, String operator);

    /**
     * 等级历史
     *
     * @param leadId 线索ID
     * @return 记录
     */
    public List<Map<String, Object>> scoreLog(Long leadId);

    /**
     * 评分规则列表
     *
     * @return 规则
     */
    public List<AbrLeadScoreRule> selectRules();

    /**
     * 修改评分规则（校验参数键与取值；分级线不能停用）
     *
     * @param rule 规则
     * @return 结果
     */
    public int updateRule(AbrLeadScoreRule rule);
}
