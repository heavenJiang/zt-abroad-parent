package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrProgramBenchmark;

/**
 * 项目录取参考线 服务层
 * 
 * @author ygb
 */
public interface IAbrProgramBenchmarkService
{
    /**
     * 查询参考线列表
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
     * 新增参考线（每个项目一条）
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
     * 批量删除参考线
     * 
     * @param benchmarkIds 参考线ID
     * @param operator 操作人
     * @return 结果
     */
    public int deleteAbrProgramBenchmarkByIds(Long[] benchmarkIds, String operator);
}
