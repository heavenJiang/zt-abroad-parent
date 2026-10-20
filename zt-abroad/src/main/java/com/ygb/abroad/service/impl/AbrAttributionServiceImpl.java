package com.ygb.abroad.service.impl;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.vo.AbrAttributionRow;
import com.ygb.abroad.mapper.AbrAttributionMapper;
import com.ygb.abroad.service.IAbrAttributionService;
import com.ygb.common.utils.StringUtils;

/**
 * 归因看板 服务层实现（只读，按需从业务表计算，不建汇总表）
 * 
 * @author ygb
 */
@Service
public class AbrAttributionServiceImpl implements IAbrAttributionService
{
    @Autowired
    private AbrAttributionMapper attributionMapper;

    /** 自然推荐渠道码（分享、推荐码、offer 卡等，新访客“从哪里知道我们”） */
    @Value("${abroad.attribution.natural-channels:share,ref,offer}")
    private List<String> naturalChannels;

    @Override
    public Map<String, Object> funnel(String beginDate, String endDate, String groupBy)
    {
        AbrAttributionRules.Range range = AbrAttributionRules.range(beginDate, endDate, today());
        String group = AbrAttributionRules.group(groupBy);
        String b = range.begin().toString();
        String e = range.end().toString();
        List<AbrAttributionRow> rows = AbrAttributionRules.merge(attributionMapper.selectFunnel(b, e, group),
                attributionMapper.selectContentMetrics(b, e, group));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("beginDate", b);
        data.put("endDate", e);
        data.put("groupBy", group);
        data.put("rows", rows);
        data.put("total", AbrAttributionRules.total(rows));
        return data;
    }

    @Override
    public Map<String, Object> product(Integer weeks)
    {
        int n = weeks == null ? 12 : Math.max(AbrAttributionRules.MIN_WEEKS, Math.min(AbrAttributionRules.MAX_WEEKS, weeks));
        LocalDate today = today();
        LocalDate since = AbrAttributionRules.weekStart(today).minusWeeks(n - 1);
        List<Map<String, Object>> weekly = AbrAttributionRules.weeks(attributionMapper.selectWeeklyActive(since.toString()), since, today);
        List<String> channels = naturalChannels.stream().map(String::trim).filter(StringUtils::isNotBlank).toList();
        Map<String, Object> natural = channels.isEmpty() ? Map.of()
                : attributionMapper.countNaturalVisitors(since.toString(), today.toString(), channels);
        Map<String, Object> share = attributionMapper.countSharers(since.toString(), today.toString());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("beginDate", since.toString());
        data.put("endDate", today.toString());
        data.put("weeks", weekly);
        data.put("thisWeek", weekly.get(weekly.size() - 1).get("active"));
        data.put("lastWeek", weekly.size() > 1 ? weekly.get(weekly.size() - 2).get("active") : 0L);
        data.put("cohorts", AbrAttributionRules.cohorts(attributionMapper.selectActiveDays(since.toString()), today));
        data.put("naturalChannels", channels);
        data.put("newVisitors", AbrAttributionRules.num(natural.get("total")));
        data.put("naturalVisitors", AbrAttributionRules.num(natural.get("natural_count")));
        data.put("activeUsers", AbrAttributionRules.num(share.get("active")));
        data.put("sharers", AbrAttributionRules.num(share.get("sharers")));
        return data;
    }

    private static LocalDate today()
    {
        return LocalDate.now(AbrLookupTools.BEIJING);
    }
}
