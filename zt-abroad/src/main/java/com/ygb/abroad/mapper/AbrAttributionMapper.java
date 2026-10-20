package com.ygb.abroad.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.vo.AbrAttributionRow;

/**
 * 归因看板与选校单活跃 数据层（M9-03）
 * 
 * @author ygb
 */
public interface AbrAttributionMapper
{
    /**
     * 记录一次打开选校单（同一访客同一天一行，项目数取当前选校单）
     * 
     * @param visitorId 访客（主访客）
     * @param activeDate 日期（北京时间 yyyy-MM-dd）
     * @return 结果
     */
    public int upsertPlanActive(@Param("visitorId") Long visitorId, @Param("activeDate") String activeDate);

    /**
     * 归因漏斗：期间首次进入的主访客按分组统计各步人数
     * 
     * @param beginDate 开始日期（yyyy-MM-dd，含）
     * @param endDate 结束日期（yyyy-MM-dd，含）
     * @param groupBy 分组（channel / account / content / playbook）
     * @return 每组一行
     */
    public List<AbrAttributionRow> selectFunnel(@Param("beginDate") String beginDate, @Param("endDate") String endDate,
            @Param("groupBy") String groupBy);

    /**
     * 期间发布的内容数与回填的效果数据，按同一分组
     * 
     * @param beginDate 开始日期（含）
     * @param endDate 结束日期（含）
     * @param groupBy 分组
     * @return 每组一行（只含 groupKey、groupLabel 与内容指标）
     */
    public List<AbrAttributionRow> selectContentMetrics(@Param("beginDate") String beginDate, @Param("endDate") String endDate,
            @Param("groupBy") String groupBy);

    /**
     * 选校单活跃天（当时选校单至少 1 个项目），只取首次活跃不早于 since 的访客；用于每周活跃与留存
     * 
     * @param since 开始日期（yyyy-MM-dd）
     * @return visitor_id、first_date、active_date（日期为 yyyy-MM-dd 字符串）
     */
    public List<Map<String, Object>> selectActiveDays(@Param("since") String since);

    /**
     * 每周活跃选校单（周一开始）：active 当时有项目的访客数、opened 打开过的访客数
     * 
     * @param since 开始日期（周一，yyyy-MM-dd）
     * @return week（yyyy-MM-dd）、active、opened
     */
    public List<Map<String, Object>> selectWeeklyActive(@Param("since") String since);

    /**
     * 期间新访客与其中来自自然推荐渠道的人数
     * 
     * @param beginDate 开始日期（含）
     * @param endDate 结束日期（含）
     * @param channels 自然推荐渠道码
     * @return total、natural_count
     */
    public Map<String, Object> countNaturalVisitors(@Param("beginDate") String beginDate, @Param("endDate") String endDate,
            @Param("channels") List<String> channels);

    /**
     * 期间活跃选校单的访客数与其中开启过分享的人数
     * 
     * @param beginDate 开始日期（含）
     * @param endDate 结束日期（含）
     * @return active、sharers
     */
    public Map<String, Object> countSharers(@Param("beginDate") String beginDate, @Param("endDate") String endDate);
}
