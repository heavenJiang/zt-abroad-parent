package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrAiCallLog;
import com.ygb.abroad.domain.vo.AbrAiUsageStat;
import com.ygb.abroad.mapper.AbrAiCallLogMapper;
import com.ygb.abroad.service.IAbrAiCallLogService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;

/**
 * AI 调用日志 服务层处理
 *
 * @author ygb
 */
@Service
public class AbrAiCallLogServiceImpl implements IAbrAiCallLogService
{
    /** 统计口径时区：北京时间 */
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    /** 统计最长跨度（天） */
    private static final int MAX_STAT_DAYS = 366;

    @Autowired
    private AbrAiCallLogMapper callLogMapper;

    /**
     * 查询调用日志列表
     *
     * @param log 查询条件
     * @return 调用日志集合
     */
    @Override
    public List<AbrAiCallLog> selectAbrAiCallLogList(AbrAiCallLog log)
    {
        return callLogMapper.selectAbrAiCallLogList(log);
    }

    /**
     * 按ID查询调用日志
     *
     * @param callId 调用ID
     * @return 调用日志
     */
    @Override
    public AbrAiCallLog selectAbrAiCallLogById(Long callId)
    {
        return callLogMapper.selectAbrAiCallLogById(callId);
    }

    /**
     * 用量统计（时间边界在 Java 中按北京时间计算后传入 SQL）
     *
     * @param beginTime 开始日期 yyyy-MM-dd
     * @param endTime 结束日期 yyyy-MM-dd
     * @return 统计结果
     */
    @Override
    public Map<String, Object> selectAbrAiUsageStats(String beginTime, String endTime)
    {
        LocalDate end = StringUtils.isEmpty(endTime) ? LocalDate.now(BEIJING) : parseDate(endTime, "结束日期");
        LocalDate begin = StringUtils.isEmpty(beginTime) ? end.minusDays(6) : parseDate(beginTime, "开始日期");
        if (begin.isAfter(end))
        {
            throw new ServiceException("开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(begin, end) >= MAX_STAT_DAYS)
        {
            throw new ServiceException("统计跨度不能超过 " + MAX_STAT_DAYS + " 天");
        }
        Date from = Date.from(begin.atStartOfDay(BEIJING).toInstant());
        Date to = Date.from(end.plusDays(1).atStartOfDay(BEIJING).toInstant());
        List<AbrAiUsageStat> rows = callLogMapper.selectAbrAiUsageStats(from, to);

        Map<String, AbrAiUsageStat> daily = new TreeMap<>();
        Map<String, AbrAiUsageStat> agents = new TreeMap<>();
        AbrAiUsageStat total = empty(null, null);
        for (AbrAiUsageStat row : rows)
        {
            add(daily.computeIfAbsent(row.getDay(), d -> empty(d, null)), row);
            add(agents.computeIfAbsent(row.getAgentCode(), a -> empty(null, a)), row);
            add(total, row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("beginTime", begin.toString());
        result.put("endTime", end.toString());
        result.put("total", total);
        result.put("daily", new ArrayList<>(daily.values()));
        result.put("agents", new ArrayList<>(agents.values()));
        result.put("rows", rows);
        return result;
    }

    private static LocalDate parseDate(String text, String label)
    {
        try
        {
            return LocalDate.parse(text.trim());
        }
        catch (DateTimeParseException e)
        {
            throw new ServiceException(label + "格式应为 yyyy-MM-dd");
        }
    }

    private static AbrAiUsageStat empty(String day, String agentCode)
    {
        AbrAiUsageStat s = new AbrAiUsageStat();
        s.setDay(day);
        s.setAgentCode(agentCode);
        s.setCalls(0L);
        s.setSuccessCalls(0L);
        s.setErrorCalls(0L);
        s.setPromptTokens(0L);
        s.setCompletionTokens(0L);
        s.setCost(BigDecimal.ZERO);
        return s;
    }

    private static void add(AbrAiUsageStat target, AbrAiUsageStat row)
    {
        target.setCalls(target.getCalls() + nz(row.getCalls()));
        target.setSuccessCalls(target.getSuccessCalls() + nz(row.getSuccessCalls()));
        target.setErrorCalls(target.getErrorCalls() + nz(row.getErrorCalls()));
        target.setPromptTokens(target.getPromptTokens() + nz(row.getPromptTokens()));
        target.setCompletionTokens(target.getCompletionTokens() + nz(row.getCompletionTokens()));
        target.setCost(target.getCost().add(row.getCost() == null ? BigDecimal.ZERO : row.getCost()));
    }

    private static long nz(Long value)
    {
        return value == null ? 0L : value;
    }
}
