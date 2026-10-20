package com.ygb.abroad.service;

import java.util.Map;

/**
 * 归因看板 服务层（M9-03）
 * 
 * @author ygb
 */
public interface IAbrAttributionService
{
    /**
     * 获客漏斗：期间首次进入的访客按首次渠道码与内容编号归因，统计到工具、选校单、登录、转化时刻、预约、签约各步的人数，
     * 并附同一分组期间发布的内容数与回填的效果数据
     * 
     * @param beginDate 开始日期（yyyy-MM-dd，含；不填为最近 30 天）
     * @param endDate 结束日期（yyyy-MM-dd，含）
     * @param groupBy 分组（channel 渠道 / account 账号 / content 内容 / playbook 剧本）
     * @return beginDate、endDate、groupBy、rows、total
     */
    public Map<String, Object> funnel(String beginDate, String endDate, String groupBy);

    /**
     * 产品指标：每周活跃选校单（北极星）、按首次活跃周的 7 / 30 日留存、分享率、自然推荐占比
     * 
     * @param weeks 看最近几周（4–26，默认 12）
     * @return weeks、cohorts、thisWeek、lastWeek、beginDate、endDate、newVisitors、naturalVisitors、naturalChannels、activeUsers、sharers
     */
    public Map<String, Object> product(Integer weeks);
}
