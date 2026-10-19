package com.ygb.abroad.mapper;

import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * 编排游标 数据层
 * 
 * @author ygb
 */
public interface AbrGrowthCursorMapper
{
    /**
     * 锁定并读取游标（另一轮持有时不等待，返回 null）
     * 
     * @param cursorName 游标名
     * @return last_event_id、state（JSON 文本）
     */
    public Map<String, Object> lockCursor(String cursorName);

    /**
     * 更新游标
     * 
     * @param cursorName 游标名
     * @param lastEventId 已读取到的事件ID
     * @param state 状态（JSON 文本）
     * @return 影响行数
     */
    public int updateCursor(@Param("cursorName") String cursorName, @Param("lastEventId") Long lastEventId, @Param("state") String state);
}
