package com.ygb.abroad.service;

import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrFieldHistory;
import com.ygb.abroad.domain.AbrFieldSource;
import com.ygb.abroad.domain.AbrSourceSnapshot;

/**
 * 源信息存档与字段历史 服务层（M2-09）
 * 
 * @author ygb
 */
public interface IAbrSourceArchiveService
{
    /** BaseEntity.params 中的生效方式键（manual / ai_prefill / import / auto / veto_window / rollback） */
    public static final String PARAM_CHANGE_MODE = "changeMode";

    /**
     * 保存一份原始依据；同一网址内容不变时复用原存档并更新最近一次看到的时间
     *
     * @param sourceUrl 请求的网址（接口地址不能带密钥）
     * @param finalUrl 最终网址
     * @param kind web / api / file
     * @param httpStatus HTTP 状态
     * @param contentType 内容类型
     * @param title 标题或说明
     * @param content 内容（网页纯文本、接口 JSON、文件文字）
     * @param operator 抓取者
     * @return 存档ID
     */
    public Long archive(String sourceUrl, String finalUrl, String kind, Integer httpStatus, String contentType, String title, String content,
            String operator);

    public AbrSourceSnapshot selectSnapshot(Long snapshotId);

    /**
     * 同一网址在最近若干天内的存档（用于避免重复请求官方接口）；没有为 null
     *
     * @param sourceUrl 网址
     * @param days 天数
     * @return 存档
     */
    public AbrSourceSnapshot selectRecent(String sourceUrl, int days);

    /**
     * 记录字段变化：逐个比较旧对象与新对象的字段，有变化的写一条历史（新增时旧对象为 null）
     *
     * @param tableName 表
     * @param recordId 记录ID
     * @param oldEntity 修改前（新增为 null）
     * @param newEntity 修改后
     * @param fields 要比较的字段（Java 属性名）
     * @param sources 字段来源（字段名 → 来源，可为空），用于带出依据
     * @param changeMode 生效方式
     * @param operator 操作人
     * @return 写入的历史条数
     */
    public int recordChanges(String tableName, Long recordId, Object oldEntity, Object newEntity, List<String> fields,
            Map<String, AbrFieldSource> sources, String changeMode, String operator);

    public List<AbrFieldHistory> selectHistory(String tableName, Long recordId);

    /**
     * 清理存档：最近一次看到超过保留年限、且不再被当前字段来源引用的
     *
     * @return 删除条数
     */
    public int purgeExpired();
}
