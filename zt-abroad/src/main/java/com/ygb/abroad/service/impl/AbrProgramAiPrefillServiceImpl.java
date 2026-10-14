package com.ygb.abroad.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.ygb.abroad.ai.AiContext;
import com.ygb.abroad.ai.AiGateway;
import com.ygb.abroad.ai.AiResult;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.integration.AbrFetchException;
import com.ygb.abroad.integration.AbrWebPage;
import com.ygb.abroad.integration.AbrWebPageFetcher;
import com.ygb.abroad.mapper.AbrProgramMapper;
import com.ygb.abroad.service.IAbrProgramAiPrefillService;
import com.ygb.common.core.domain.entity.SysDictData;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DateUtils;
import com.ygb.common.utils.DictUtils;
import com.ygb.common.utils.StringUtils;

/**
 * 项目 AI 预填（catalog_extractor）服务层处理
 * <p>
 * 抓取官网页面（{@link AbrWebPageFetcher}，最多 15 秒）→ AI 网关抽取字段 + 原文引句 → 事后校验（{@link AbrAiPrefillChecker}：
 * 引句必须在页面原文中出现、取值符合人工保存规则）→ 返回提议值。总时限 30 秒；不写任何项目、申请要求表。
 * </p>
 *
 * @author ygb
 */
@Service
public class AbrProgramAiPrefillServiceImpl implements IAbrProgramAiPrefillService
{
    private static final Logger log = LoggerFactory.getLogger(AbrProgramAiPrefillServiceImpl.class);

    /** Agent 编码 */
    public static final String AGENT_CODE = "catalog_extractor";

    /** 调用日志的业务类型 */
    public static final String BIZ_TYPE = "program_prefill";

    /** 总时限（毫秒）：抓取 + AI 抽取 */
    public static final long TOTAL_TIMEOUT_MS = 30000L;

    /** 留给 AI 抽取的最少时间（毫秒），不足时直接按超时处理 */
    private static final long MIN_AI_MS = 1000L;

    @Autowired
    private AbrWebPageFetcher fetcher;

    @Autowired
    private AiGateway gateway;

    @Autowired
    private AbrProgramMapper programMapper;

    @Autowired
    @Qualifier("threadPoolTaskExecutor")
    private Executor executor;

    /**
     * 抓取官网页面并 AI 预填
     *
     * @param url 项目官网页面链接
     * @param programId 项目ID（可为空）
     * @param operator 操作人
     * @return 预填结果
     */
    @Override
    public Map<String, Object> prefill(String url, Long programId, String operator)
    {
        long start = System.nanoTime();
        String hint = "";
        if (programId != null)
        {
            AbrProgram program = programMapper.selectAbrProgramById(programId);
            if (program == null)
            {
                throw new ServiceException("项目不存在或已删除");
            }
            hint = programHint(program);
        }

        AbrWebPage page;
        try
        {
            page = fetcher.fetch(url);
        }
        catch (AbrFetchException e)
        {
            log.info("AI 预填抓取失败 url={} reason={}：{}", url, e.getReason(), e.getMessage());
            throw new ServiceException(e.getMessage());
        }
        if (StringUtils.isBlank(page.getText()))
        {
            throw new ServiceException("页面打不开：页面中没有可读的文字（可能需要在浏览器中执行脚本才能显示），请手动录入");
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("url", page.getFinalUrl());
        input.put("programHint", StringUtils.isEmpty(hint) ? "（未指定，按页面主体项目抽取）" : hint);
        input.put("pageTitle", page.getTitle());
        input.put("pageText", page.getText());
        AiContext context = AiContext.of(BIZ_TYPE, programId == null ? "" : String.valueOf(programId), operator);

        long remaining = TOTAL_TIMEOUT_MS - elapsedMs(start);
        if (remaining < MIN_AI_MS)
        {
            throw timeout();
        }
        CompletableFuture<AiResult> future = CompletableFuture.supplyAsync(() -> gateway.invoke(AGENT_CODE, input, context), executor);
        AiResult result;
        try
        {
            result = future.get(remaining, TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e)
        {
            future.cancel(true);
            throw timeout();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new ServiceException("AI 预填已中断，请重试或手动录入");
        }
        catch (ExecutionException e)
        {
            log.error("AI 预填调用异常 url={}", url, e.getCause());
            throw new ServiceException("AI 预填失败：调用异常，请稍后重试或手动录入");
        }
        if (!result.isSuccess())
        {
            throw new ServiceException("AI 预填失败：" + StringUtils.defaultString(result.getMessage(), result.getStatus()) + "。请手动录入");
        }

        List<Map<String, Object>> fields = new AbrAiPrefillChecker(AbrProgramAiPrefillServiceImpl::dictValues).check(result.getOutput(), page.getText());
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put(AbrAiPrefillChecker.STATUS_FOUND, 0);
        summary.put(AbrAiPrefillChecker.STATUS_NOT_FOUND, 0);
        summary.put(AbrAiPrefillChecker.STATUS_DISCARDED, 0);
        for (Map<String, Object> f : fields)
        {
            summary.merge((String) f.get("status"), 1, Integer::sum);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", page.getFinalUrl());
        data.put("requestUrl", page.getRequestUrl());
        data.put("pageTitle", page.getTitle());
        data.put("truncated", page.isTruncated());
        data.put("fetchedAt", DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, page.getFetchedAt()));
        data.put("fetchMs", page.getElapsedMs());
        data.put("elapsedMs", elapsedMs(start));
        data.put("callId", result.getCallId());
        data.put("modelUsed", result.getModelUsed());
        data.put("summary", summary);
        data.put("fields", fields);
        return data;
    }

    /**
     * 目标项目提示：院校英文名 · 项目英文名（学位）
     */
    private static String programHint(AbrProgram program)
    {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotEmpty(program.getSchoolNameEn()))
        {
            sb.append(program.getSchoolNameEn());
        }
        else if (StringUtils.isNotEmpty(program.getSchoolName()))
        {
            sb.append(program.getSchoolName());
        }
        if (StringUtils.isNotEmpty(program.getProgramNameEn()))
        {
            sb.append(sb.length() > 0 ? " · " : "").append(program.getProgramNameEn());
        }
        if (StringUtils.isNotEmpty(program.getDegree()))
        {
            sb.append("（").append(program.getDegree()).append("）");
        }
        return sb.toString();
    }

    /**
     * 字典有效编码（只取正常状态）；缓存未加载时返回 null，由校验器使用内置编码
     */
    private static Set<String> dictValues(String dictType)
    {
        List<SysDictData> datas = DictUtils.getDictCache(dictType);
        if (datas == null || datas.isEmpty())
        {
            return null;
        }
        return datas.stream().filter(d -> d.getStatus() == null || "0".equals(d.getStatus())).map(SysDictData::getDictValue)
                .collect(Collectors.toSet());
    }

    private static long elapsedMs(long start)
    {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private static ServiceException timeout()
    {
        return new ServiceException("AI 预填超时：" + (TOTAL_TIMEOUT_MS / 1000) + " 秒内没有完成，请稍后重试或手动录入");
    }
}
