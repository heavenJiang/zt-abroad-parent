package com.ygb.abroad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrProgramBenchmark;

/**
 * 项目录取参考线 数据层
 * 
 * @author ygb
 */
public interface AbrProgramBenchmarkMapper
{
    /**
     * 查询参考线列表（含院校、项目名）
     * 
     * @param benchmark 查询条件
     * @return 参考线集合
     */
    public List<AbrProgramBenchmark> selectAbrProgramBenchmarkList(AbrProgramBenchmark benchmark);

    /**
     * 按ID查询参考线
     * 
     * @param benchmarkId 参考线ID
     * @return 参考线
     */
    public AbrProgramBenchmark selectAbrProgramBenchmarkById(Long benchmarkId);

    /**
     * 按项目查询当前参考线
     * 
     * @param programId 项目ID
     * @return 参考线，没有返回 null
     */
    public AbrProgramBenchmark selectAbrProgramBenchmarkByProgramId(Long programId);

    /**
     * 按项目批量查询当前参考线
     * 
     * @param programIds 项目ID
     * @return 参考线集合
     */
    public List<AbrProgramBenchmark> selectAbrProgramBenchmarkByProgramIds(@Param("programIds") List<Long> programIds);

    /**
     * 新增参考线
     * 
     * @param benchmark 参考线
     * @return 结果
     */
    public int insertAbrProgramBenchmark(AbrProgramBenchmark benchmark);

    /**
     * 修改参考线（项目不可改）
     * 
     * @param benchmark 参考线
     * @return 结果
     */
    public int updateAbrProgramBenchmark(AbrProgramBenchmark benchmark);

    /**
     * 批量删除参考线（逻辑删除）
     * 
     * @param benchmarkIds 参考线ID
     * @param updateBy 操作人
     * @return 结果
     */
    public int deleteAbrProgramBenchmarkByIds(@Param("benchmarkIds") Long[] benchmarkIds, @Param("updateBy") String updateBy);
}
