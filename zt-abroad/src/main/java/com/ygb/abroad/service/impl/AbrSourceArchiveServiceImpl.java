package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrFieldHistory;
import com.ygb.abroad.domain.AbrFieldSource;
import com.ygb.abroad.domain.AbrSourceSnapshot;
import com.ygb.abroad.mapper.AbrSourceArchiveMapper;
import com.ygb.abroad.service.IAbrSourceArchiveService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 源信息存档与字段历史 服务层实现
 * 
 * @author ygb
 */
@Service
public class AbrSourceArchiveServiceImpl implements IAbrSourceArchiveService
{
    private static final Logger log = LoggerFactory.getLogger(AbrSourceArchiveServiceImpl.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 单份存档最多保存的字符数（超过截断并注明） */
    static final int MAX_CONTENT = 2_000_000;

    @Autowired
    private AbrSourceArchiveMapper archiveMapper;

    /** 存档保留年限（从最近一次看到算起） */
    @Value("${abroad.source.retention-years:2}")
    private int retentionYears;

    @Override
    public Long archive(String sourceUrl, String finalUrl, String kind, Integer httpStatus, String contentType, String title, String content,
            String operator)
    {
        if (StringUtils.isBlank(sourceUrl) || sourceUrl.length() > 1000)
        {
            throw new ServiceException("存档网址为空或过长");
        }
        if (sourceUrl.toLowerCase().contains("api_key=") || sourceUrl.toLowerCase().contains("apikey="))
        {
            throw new ServiceException("存档网址不能包含密钥");
        }
        String text = content == null ? "" : content;
        String remark = null;
        if (text.length() > MAX_CONTENT)
        {
            text = text.substring(0, MAX_CONTENT);
            remark = "内容超过 " + MAX_CONTENT + " 字符，已截断";
        }
        AbrSourceSnapshot s = new AbrSourceSnapshot();
        s.setSourceUrl(sourceUrl);
        s.setFinalUrl(StringUtils.substring(finalUrl, 0, 1000));
        s.setSourceKind(kind);
        s.setHttpStatus(httpStatus);
        s.setContentType(StringUtils.substring(contentType, 0, 100));
        s.setContentHash(sha256(text));
        s.setContent(text);
        s.setContentSize(text.length());
        s.setTitle(StringUtils.substring(title, 0, 300));
        s.setFetchedBy(operator);
        s.setRemark(remark);
        // 同一网址、同样内容时 on conflict 更新最近一次看到的时间，并返回原存档 ID
        archiveMapper.insertSnapshot(s);
        return s.getSnapshotId();
    }

    static String sha256(String text)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public AbrSourceSnapshot selectSnapshot(Long snapshotId)
    {
        AbrSourceSnapshot s = snapshotId == null ? null : archiveMapper.selectSnapshotById(snapshotId);
        if (s == null)
        {
            throw new ServiceException("存档不存在或已超过保留期清理");
        }
        return s;
    }

    @Override
    public AbrSourceSnapshot selectRecent(String sourceUrl, int days)
    {
        LocalDateTime since = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusDays(Math.max(0, days));
        return archiveMapper.selectRecentSnapshot(sourceUrl, Date.from(since.atZone(ZoneId.of("Asia/Shanghai")).toInstant()));
    }

    @Override
    public int recordChanges(String tableName, Long recordId, Object oldEntity, Object newEntity, List<String> fields,
            Map<String, AbrFieldSource> sources, String changeMode, String operator)
    {
        int n = 0;
        BeanWrapperImpl oldBean = oldEntity == null ? null : new BeanWrapperImpl(oldEntity);
        BeanWrapperImpl newBean = new BeanWrapperImpl(newEntity);
        for (String field : fields)
        {
            String before = oldBean == null ? null : text(oldBean.getPropertyValue(field));
            String after = text(newBean.getPropertyValue(field));
            if (same(before, after) || (oldBean == null && after == null))
            {
                continue;
            }
            AbrFieldHistory h = new AbrFieldHistory();
            h.setTableName(tableName);
            h.setRecordId(recordId);
            h.setFieldName(field);
            h.setOldValue(before);
            h.setNewValue(after);
            AbrFieldSource src = sources == null ? null : sources.get(field);
            if (src != null)
            {
                h.setSnapshotId(src.getSnapshotId());
                h.setSourceUrl(StringUtils.substring(src.getSourceUrl(), 0, 1000));
                h.setQuote(StringUtils.substring(src.getQuote(), 0, 2000));
            }
            h.setChangeMode(StringUtils.defaultIfBlank(changeMode, "manual"));
            h.setOperator(operator);
            archiveMapper.insertHistory(h);
            n++;
        }
        return n;
    }

    /**
     * 字段值转文本：数字去掉末尾 0，JSON 统一格式，空串视为空
     */
    static String text(Object v)
    {
        if (v == null)
        {
            return null;
        }
        if (v instanceof BigDecimal b)
        {
            return b.stripTrailingZeros().toPlainString();
        }
        if (v instanceof Date d)
        {
            return LocalDateTime.ofInstant(d.toInstant(), ZoneId.of("Asia/Shanghai")).toString();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty())
        {
            return null;
        }
        if (s.startsWith("{") || s.startsWith("["))
        {
            try
            {
                JsonNode n = MAPPER.readTree(s);
                return MAPPER.writeValueAsString(n);
            }
            catch (RuntimeException e)
            {
                return s;
            }
        }
        return s;
    }

    static boolean same(String a, String b)
    {
        if (Objects.equals(a, b))
        {
            return true;
        }
        if (a == null || b == null)
        {
            return false;
        }
        if (a.startsWith("{") || a.startsWith("["))
        {
            try
            {
                return MAPPER.readTree(a).equals(MAPPER.readTree(b));
            }
            catch (RuntimeException e)
            {
                return false;
            }
        }
        return false;
    }

    @Override
    public List<AbrFieldHistory> selectHistory(String tableName, Long recordId)
    {
        return archiveMapper.selectHistory(tableName, recordId);
    }

    @Override
    public int purgeExpired()
    {
        LocalDateTime cutoff = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusYears(Math.max(1, retentionYears));
        int n = archiveMapper.purgeSnapshots(Date.from(cutoff.atZone(ZoneId.of("Asia/Shanghai")).toInstant()));
        if (n > 0)
        {
            log.info("源信息存档清理 {} 份（最近一次看到早于 {}）", n, cutoff.toLocalDate());
        }
        return n;
    }
}
