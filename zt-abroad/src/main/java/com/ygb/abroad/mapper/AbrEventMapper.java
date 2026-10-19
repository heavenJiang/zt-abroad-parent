package com.ygb.abroad.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrEvent;

/**
 * 业务事件 数据层
 * 
 * @author ygb
 */
public interface AbrEventMapper
{
    /**
     * 新增事件；带去重键且同一访客同类事件已存在时不写入
     * 
     * @param event 事件
     * @return 写入行数（已存在为 0）
     */
    public int insertAbrEvent(AbrEvent event);

    /**
     * 访客已记录的转化时刻编码
     * 
     * @param visitorId 访客ID
     * @return 时刻编码
     */
    public List<String> selectMomentCodes(Long visitorId);

    /**
     * 访客最早一条待展示的推荐
     * 
     * @param visitorId 访客ID
     * @return 事件，没有返回 null
     */
    public AbrEvent selectPendingRecommendation(Long visitorId);

    /**
     * 访客最近一次推荐展示时间
     * 
     * @param visitorId 访客ID
     * @return 时间，从未展示返回 null
     */
    public Date selectLastShownTime(Long visitorId);

    /**
     * 把待展示的推荐标为已展示
     * 
     * @param eventId 事件ID
     * @return 影响行数（已被其他请求展示为 0）
     */
    public int markRecommendationShown(Long eventId);

    /**
     * 访客某些类型事件在某时刻之后的发生时间（判断反复修改）
     * 
     * @param visitorId 访客ID
     * @param eventTypes 事件类型
     * @param since 起始时刻
     * @return 发生时间
     */
    public List<Date> selectEventTimes(@Param("visitorId") Long visitorId, @Param("eventTypes") List<String> eventTypes, @Param("since") Date since);

    /**
     * 新增系统事件（无访客，如剧本切换、日历节点）；同类事件去重键已存在时不写入
     * 
     * @param event 事件
     * @return 写入行数（已存在为 0）
     */
    public int insertSystemEvent(AbrEvent event);

    /**
     * 编排器读取：某ID之后的事件，按ID升序；settled 表示写入已超过静置秒数
     * 
     * @param afterId 读取位置（不含）
     * @param settleSeconds 静置秒数
     * @param limit 最多条数
     * @return 事件
     */
    public List<AbrEvent> selectEventsAfter(@Param("afterId") Long afterId, @Param("settleSeconds") int settleSeconds, @Param("limit") int limit);
}
