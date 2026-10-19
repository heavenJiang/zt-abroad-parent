package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrCalendarEntry;

/**
 * 营销日历 服务层
 * 
 * @author ygb
 */
public interface IAbrCalendarService
{
    /**
     * 查询日历条目列表
     * 
     * @param entry 查询条件
     * @return 日历条目集合
     */
    public List<AbrCalendarEntry> selectAbrCalendarEntryList(AbrCalendarEntry entry);

    /**
     * 按ID查询日历条目
     * 
     * @param entryId 日历条目ID
     * @return 日历条目
     */
    public AbrCalendarEntry selectAbrCalendarEntryById(Long entryId);

    /**
     * 新增日历条目
     * 
     * @param entry 日历条目
     * @return 结果
     */
    public int insertAbrCalendarEntry(AbrCalendarEntry entry);

    /**
     * 修改日历条目
     * 
     * @param entry 日历条目
     * @return 结果
     */
    public int updateAbrCalendarEntry(AbrCalendarEntry entry);

    /**
     * 批量删除日历条目
     * 
     * @param entryIds 日历条目ID
     * @return 结果
     */
    public int deleteAbrCalendarEntryByIds(Long[] entryIds);
}
