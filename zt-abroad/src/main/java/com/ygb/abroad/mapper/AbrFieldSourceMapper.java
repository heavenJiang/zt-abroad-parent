package com.ygb.abroad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrFieldSource;

/**
 * 字段来源 数据层
 * 
 * @author ygb
 */
public interface AbrFieldSourceMapper
{
    /**
     * 查询某条记录的全部字段来源
     * 
     * @param tableName 记录所在表
     * @param recordId 记录ID
     * @return 字段来源集合
     */
    public List<AbrFieldSource> selectAbrFieldSourceByRecord(@Param("tableName") String tableName, @Param("recordId") Long recordId);

    /**
     * 新增或更新字段来源（同一记录同一字段只保留最新一条）
     * 
     * @param source 字段来源
     * @return 影响行数
     */
    public int upsertAbrFieldSource(AbrFieldSource source);

    /**
     * 删除某条记录中不再保留来源的字段
     * 
     * @param tableName 记录所在表
     * @param recordId 记录ID
     * @param keepFields 保留的字段名（为空时删除该记录全部来源）
     * @return 影响行数
     */
    public int deleteAbrFieldSourceExcept(@Param("tableName") String tableName, @Param("recordId") Long recordId, @Param("keepFields") List<String> keepFields);
}
