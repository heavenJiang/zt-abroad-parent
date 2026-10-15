package com.ygb.abroad.service.impl;

import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrProgramBenchmark;
import com.ygb.abroad.mapper.AbrProgramBenchmarkMapper;
import com.ygb.abroad.mapper.AbrProgramMapper;
import com.ygb.abroad.service.IAbrProgramBenchmarkService;
import com.ygb.common.exception.ServiceException;

/**
 * 项目录取参考线 服务层实现
 * 
 * @author ygb
 */
@Service
public class AbrProgramBenchmarkServiceImpl implements IAbrProgramBenchmarkService
{
    /** 参考线来源（字典 abr_benchmark_source） */
    static final Set<String> SOURCES = Set.of("own_cases", "official_stats", "consultant_difficulty");

    @Autowired
    private AbrProgramBenchmarkMapper benchmarkMapper;

    @Autowired
    private AbrProgramMapper programMapper;

    @Override
    public List<AbrProgramBenchmark> selectAbrProgramBenchmarkList(AbrProgramBenchmark benchmark)
    {
        return benchmarkMapper.selectAbrProgramBenchmarkList(benchmark);
    }

    @Override
    public AbrProgramBenchmark selectAbrProgramBenchmarkById(Long benchmarkId)
    {
        return benchmarkMapper.selectAbrProgramBenchmarkById(benchmarkId);
    }

    @Override
    public int insertAbrProgramBenchmark(AbrProgramBenchmark benchmark)
    {
        checkSource(benchmark.getSource());
        if (programMapper.selectAbrProgramById(benchmark.getProgramId()) == null)
        {
            throw new ServiceException("项目不存在");
        }
        if (benchmarkMapper.selectAbrProgramBenchmarkByProgramId(benchmark.getProgramId()) != null)
        {
            throw new ServiceException("该项目已有参考线，请直接修改");
        }
        try
        {
            return benchmarkMapper.insertAbrProgramBenchmark(benchmark);
        }
        catch (DuplicateKeyException e)
        {
            throw new ServiceException("该项目已有参考线，请直接修改");
        }
    }

    @Override
    public int updateAbrProgramBenchmark(AbrProgramBenchmark benchmark)
    {
        checkSource(benchmark.getSource());
        if (benchmark.getBenchmarkId() == null)
        {
            throw new ServiceException("参考线ID不能为空");
        }
        return benchmarkMapper.updateAbrProgramBenchmark(benchmark);
    }

    @Override
    public int deleteAbrProgramBenchmarkByIds(Long[] benchmarkIds, String operator)
    {
        return benchmarkMapper.deleteAbrProgramBenchmarkByIds(benchmarkIds, operator);
    }

    private static void checkSource(String source)
    {
        if (!SOURCES.contains(source))
        {
            throw new ServiceException("来源应为工作室案例、官方统计或顾问定档");
        }
    }
}
