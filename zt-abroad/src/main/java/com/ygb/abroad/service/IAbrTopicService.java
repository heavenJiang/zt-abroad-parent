package com.ygb.abroad.service;

import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrTopic;

/**
 * 选题排期 topic_planner 服务层：员工维护选题；按剧本与院校库为各账号排未来若干天的选题
 *
 * @author ygb
 */
public interface IAbrTopicService
{
    /**
     * 查询选题列表
     *
     * @param topic 查询条件
     * @return 选题集合
     */
    public List<AbrTopic> selectAbrTopicList(AbrTopic topic);

    /**
     * 按ID查询选题
     *
     * @param topicId 选题ID
     * @return 选题
     */
    public AbrTopic selectAbrTopicById(Long topicId);

    /**
     * 员工插入选题（来源 manual；条次排在当天最后）
     *
     * @param topic 选题
     * @return 结果
     */
    public int insertAbrTopic(AbrTopic topic);

    /**
     * 修改选题（已写稿的不能改；改日期时条次排到新日期最后）
     *
     * @param topic 选题
     * @return 结果
     */
    public int updateAbrTopic(AbrTopic topic);

    /**
     * 删除还没写稿的选题
     *
     * @param topicIds 选题ID
     * @return 删除条数
     */
    public int deleteAbrTopicByIds(Long[] topicIds);

    /**
     * 排期：为在用的账号（或指定账号）补齐未来若干天的空位；replan 时先取消剧本已不再生效的待写稿选题
     *
     * @param accountId 账号ID（null 为全部在用账号）
     * @param replan 是否重排（剧本切换时）
     * @param operator 操作人（编排器为 topic_planner）
     * @return 各账号结果：空位数、AI 选题数、备用选题数、未填数、丢弃原因、AI 调用
     */
    public Map<String, Object> plan(Long accountId, boolean replan, String operator);
}
