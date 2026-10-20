package com.ygb.abroad.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrChannelAccount;
import com.ygb.abroad.domain.AbrContent;
import com.ygb.abroad.domain.AbrEvent;
import com.ygb.abroad.domain.AbrPublishRecord;
import com.ygb.abroad.domain.vo.AbrMetricsImportRow;
import com.ygb.abroad.growth.MarketingCalendar;
import com.ygb.abroad.mapper.AbrChannelAccountMapper;
import com.ygb.abroad.mapper.AbrContentMapper;
import com.ygb.abroad.mapper.AbrPublishRecordMapper;
import com.ygb.abroad.service.IAbrEventService;
import com.ygb.abroad.service.IAbrPublishService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DateUtils;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 发布记录 服务层实现
 *
 * 每天发布上限按北京时间自然日统计该账号的待发布（按计划时间）与已发布（按实际时间）记录；失败与撤回不计。
 *
 * @author ygb
 */
@Service
public class AbrPublishServiceImpl implements IAbrPublishService
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 一次最多导入的效果数据行数 */
    static final int MAX_METRICS_ROWS = 500;

    @Autowired
    private AbrPublishRecordMapper recordMapper;

    @Autowired
    private AbrContentMapper contentMapper;

    @Autowired
    private AbrChannelAccountMapper accountMapper;

    @Autowired
    private IAbrEventService eventService;

    /** AI 生成标识文字（发布包提示） */
    @Value("${abroad.content.ai-label:本文由 AI 辅助生成}")
    private String aiLabel;

    @Override
    public List<AbrPublishRecord> selectAbrPublishRecordList(AbrPublishRecord record)
    {
        return recordMapper.selectAbrPublishRecordList(record);
    }

    @Override
    public AbrPublishRecord selectAbrPublishRecordById(Long recordId)
    {
        return recordMapper.selectAbrPublishRecordById(recordId);
    }

    @Override
    public AbrPublishRecord schedule(Long contentId, Date plannedTime, String operator)
    {
        AbrContent c = contentId == null ? null : contentMapper.selectAbrContentById(contentId);
        if (c == null)
        {
            throw new ServiceException("内容不存在");
        }
        if (!AbrContent.STATUS_READY.equals(c.getStatus()) && !AbrContent.STATUS_PUBLISHED.equals(c.getStatus()))
        {
            throw new ServiceException("内容须先通过合规闸门" + ("Y".equals(c.getRequiresConfirm()) ? "并经人工确认" : "") + "才能安排发布");
        }
        AbrChannelAccount a = accountMapper.selectAbrChannelAccountById(c.getAccountId());
        if (a == null || !"0".equals(a.getStatus()))
        {
            throw new ServiceException("发布账号不存在或已停用");
        }
        if ("Y".equals(a.getPaused()))
        {
            throw new ServiceException("账号已暂停：" + StringUtils.defaultString(a.getPauseReason()));
        }
        Date planned = plannedTime == null ? new Date() : plannedTime;
        LocalDate day = MarketingCalendar.toLocalDate(planned);
        Date from = Date.from(day.atStartOfDay(MarketingCalendar.ZONE).toInstant());
        Date to = Date.from(day.plusDays(1).atStartOfDay(MarketingCalendar.ZONE).toInstant());
        int cap = a.getDailyPublishCap() == null ? 0 : a.getDailyPublishCap();
        if (accountMapper.countPublishBetween(a.getAccountId(), from, to, null) >= cap)
        {
            throw new ServiceException("账号 " + a.getAccountName() + " 在 " + day + " 的发布已达每天上限 " + cap + " 条");
        }
        AbrPublishRecord r = new AbrPublishRecord();
        r.setContentId(c.getContentId());
        r.setAccountId(a.getAccountId());
        r.setPublishMode(a.getPublishMode());
        r.setPlannedTime(planned);
        r.setCreateBy(operator);
        recordMapper.insertAbrPublishRecord(r);
        return recordMapper.selectAbrPublishRecordById(r.getRecordId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markPublished(AbrPublishRecord record, String operator)
    {
        AbrPublishRecord r = require(record.getRecordId());
        String url = StringUtils.trimToEmpty(record.getPostUrl());
        if (!url.isEmpty() && !url.matches("^https?://\\S+$"))
        {
            throw new ServiceException("发布链接须为 http(s) 地址");
        }
        if (record.getPublishedTime() != null && record.getPublishedTime().after(new Date(System.currentTimeMillis() + 600_000L)))
        {
            throw new ServiceException("实际发布时间不能晚于现在");
        }
        record.setPostUrl(url);
        record.setUpdateBy(operator);
        if (recordMapper.markPublished(record) == 0)
        {
            return false;
        }
        contentMapper.updateContentStatus(r.getContentId(), AbrContent.STATUS_PUBLISHED, operator);
        AbrContent c = contentMapper.selectAbrContentById(r.getContentId());
        AbrPublishRecord done = recordMapper.selectAbrPublishRecordById(r.getRecordId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("record_id", done.getRecordId());
        payload.put("content_id", c.getContentId());
        payload.put("content_code", c.getContentCode());
        payload.put("codeword", c.getCodeword());
        payload.put("content_kind", c.getContentKind());
        payload.put("playbook_code", c.getPlaybookCode());
        payload.put("account_code", c.getAccountCode());
        payload.put("platform", c.getPlatform());
        payload.put("publish_mode", done.getPublishMode());
        payload.put("post_url", done.getPostUrl());
        payload.put("published_time", DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, done.getPublishedTime()));
        eventService.recordSystemEvent(AbrEvent.TYPE_CONTENT_PUBLISHED, "record:" + done.getRecordId(), payload);
        return true;
    }

    @Override
    public boolean markFailed(Long recordId, String reason, String operator)
    {
        require(recordId);
        return recordMapper.markFailed(recordId, StringUtils.substring(StringUtils.trimToEmpty(reason), 0, 500), operator) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean withdraw(Long recordId, String reason, String operator)
    {
        AbrPublishRecord r = require(recordId);
        String text = StringUtils.trimToEmpty(reason);
        if (text.isEmpty())
        {
            throw new ServiceException("请填写撤回原因");
        }
        if (recordMapper.withdraw(recordId, StringUtils.substring(text, 0, 500), operator) == 0)
        {
            return false;
        }
        AbrContent c = contentMapper.selectAbrContentById(r.getContentId());
        if (c != null && AbrContent.STATUS_PUBLISHED.equals(c.getStatus())
                && recordMapper.countActiveByContent(c.getContentId(), AbrPublishRecord.STATUS_PUBLISHED) == 0)
        {
            contentMapper.updateContentStatus(c.getContentId(), AbrContent.STATUS_READY, operator);
        }
        return true;
    }

    @Override
    public Map<String, Object> publishPackage(Long recordId)
    {
        AbrPublishRecord r = require(recordId);
        AbrContent c = contentMapper.selectAbrContentById(r.getContentId());
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("recordId", r.getRecordId());
        pack.put("accountName", r.getAccountName());
        pack.put("plannedTime", DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, r.getPlannedTime()));
        pack.put("title", c.getTitle());
        pack.put("body", c.getBody());
        pack.put("hashtags", json(c.getHashtags()));
        pack.put("cards", json(c.getCards()));
        pack.put("toolLink", c.getToolLink());
        pack.put("contentCode", c.getContentCode());
        pack.put("codeword", c.getCodeword());
        pack.put("codewordHint", "私信回复“" + c.getCodeword() + "”");
        pack.put("aiGenerated", "Y".equals(c.getAiGenerated()));
        pack.put("aiLabel", "Y".equals(c.getAiGenerated()) ? aiLabel : null);
        pack.put("gateStatus", c.getGateStatus());
        pack.put("note", "发布后请回填链接；平台要求声明 AI 内容时一并勾选");
        return pack;
    }

    private static JsonNode json(String text)
    {
        return MAPPER.readTree(StringUtils.isBlank(text) ? "[]" : text);
    }

    private AbrPublishRecord require(Long recordId)
    {
        AbrPublishRecord r = recordId == null ? null : recordMapper.selectAbrPublishRecordById(recordId);
        if (r == null)
        {
            throw new ServiceException("发布记录不存在");
        }
        return r;
    }

    @Override
    public AbrPublishRecord updateMetrics(Long recordId, Map<String, ?> metrics, String operator)
    {
        AbrPublishRecord r = recordId == null ? null : recordMapper.selectAbrPublishRecordById(recordId);
        if (r == null)
        {
            throw new ServiceException("发布记录不存在");
        }
        writeMetrics(r, metrics, operator);
        return recordMapper.selectAbrPublishRecordById(recordId);
    }

    @Override
    public Map<String, Object> importMetrics(List<AbrMetricsImportRow> rows, String operator)
    {
        if (rows == null || rows.isEmpty())
        {
            throw new ServiceException("表格中没有数据");
        }
        if (rows.size() > MAX_METRICS_ROWS)
        {
            throw new ServiceException("一次最多导入 " + MAX_METRICS_ROWS + " 行");
        }
        int updated = 0;
        List<String> failed = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++)
        {
            AbrMetricsImportRow row = rows.get(i);
            try
            {
                AbrPublishRecord r = null;
                if (StringUtils.isNotBlank(row.getPostUrl()))
                {
                    r = recordMapper.selectByPostUrl(row.getPostUrl().trim());
                }
                if (r == null && StringUtils.isNotBlank(row.getContentCode()))
                {
                    r = recordMapper.selectLatestPublishedByContentCode(row.getContentCode().trim());
                }
                if (r == null)
                {
                    throw new ServiceException("找不到已发布的记录（按发布链接或内容编号）");
                }
                writeMetrics(r, AbrMetricsRules.fromRow(row), operator);
                updated++;
            }
            catch (ServiceException e)
            {
                failed.add("第 " + (i + 2) + " 行：" + e.getMessage());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("updated", updated);
        out.put("failed", failed);
        return out;
    }

    /**
     * 合并写入效果数据（只能写已发布的记录）
     */
    private void writeMetrics(AbrPublishRecord r, Map<String, ?> metrics, String operator)
    {
        if (!"published".equals(r.getStatus()))
        {
            throw new ServiceException("只有已发布的记录可以填写效果数据");
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        JsonNode old = StringUtils.isBlank(r.getMetrics()) ? null : MAPPER.readTree(r.getMetrics());
        if (old != null && old.isObject())
        {
            for (String k : AbrMetricsRules.KEYS)
            {
                if (old.path(k).isNumber())
                {
                    merged.put(k, old.path(k).asLong());
                }
            }
        }
        merged.putAll(AbrMetricsRules.normalize(metrics));
        recordMapper.updateMetrics(r.getRecordId(), MAPPER.writeValueAsString(merged), operator);
    }
}
