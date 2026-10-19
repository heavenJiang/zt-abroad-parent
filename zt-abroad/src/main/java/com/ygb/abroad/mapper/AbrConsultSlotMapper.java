package com.ygb.abroad.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrConsultSlot;

/**
 * 顾问时段 数据层
 *
 * @author ygb
 */
public interface AbrConsultSlotMapper
{
    /**
     * 查询时段（顾问、状态精确；params.beginTime / endTime 日期区间）
     *
     * @param slot 查询条件
     * @return 时段集合
     */
    public List<AbrConsultSlot> selectAbrConsultSlotList(AbrConsultSlot slot);

    /**
     * 按ID查询时段
     *
     * @param slotId 时段ID
     * @return 时段
     */
    public AbrConsultSlot selectAbrConsultSlotById(Long slotId);

    /**
     * 可约时段：开始时间在区间内、时长不短于 minutes
     *
     * @param from 开始时间下限
     * @param to 开始时间上限
     * @param minutes 最短时长（分钟）
     * @return 时段集合
     */
    public List<AbrConsultSlot> selectOpenSlots(@Param("from") Date from, @Param("to") Date to, @Param("minutes") int minutes);

    /**
     * 新增时段（同一顾问同一开始时间已有时跳过）
     *
     * @param slot 时段
     * @return 写入条数
     */
    public int insertSlotIgnore(AbrConsultSlot slot);

    /**
     * 占用时段（只在可约时成功）
     *
     * @param slotId 时段ID
     * @param bookingId 预约ID
     * @return 影响行数
     */
    public int occupy(@Param("slotId") Long slotId, @Param("bookingId") Long bookingId);

    /**
     * 释放时段（预约取消或改约）
     *
     * @param slotId 时段ID
     * @param bookingId 预约ID（只释放被该预约占用的）
     * @return 影响行数
     */
    public int release(@Param("slotId") Long slotId, @Param("bookingId") Long bookingId);

    /**
     * 关闭可约时段
     *
     * @param slotIds 时段ID
     * @param operator 操作人
     * @return 影响行数
     */
    public int closeSlots(@Param("slotIds") Long[] slotIds, @Param("operator") String operator);

    /**
     * 删除没有被预约的时段
     *
     * @param slotIds 时段ID
     * @return 影响行数
     */
    public int deleteFreeSlots(@Param("slotIds") Long[] slotIds);
}
