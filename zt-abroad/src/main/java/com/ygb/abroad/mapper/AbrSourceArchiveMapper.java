package com.ygb.abroad.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrFieldHistory;
import com.ygb.abroad.domain.AbrSourceSnapshot;

/**
 * 源信息存档与字段历史 数据层（M2-09）
 * 
 * @author ygb
 */
public interface AbrSourceArchiveMapper
{
    /**
     * 同一网址、同样内容的存档（只取 ID）
     */
    public Long selectSnapshotId(@Param("sourceUrl") String sourceUrl, @Param("contentHash") String contentHash);

    public int insertSnapshot(AbrSourceSnapshot snapshot);

    /**
     * 再次抓到同样内容：更新最近一次看到的时间与次数
     */
    public int touchSnapshot(@Param("snapshotId") Long snapshotId, @Param("finalUrl") String finalUrl, @Param("httpStatus") Integer httpStatus);

    public AbrSourceSnapshot selectSnapshotById(Long snapshotId);

    /**
     * 同一网址最近一次看到时间不早于 since 的存档（最新的一份）
     */
    public AbrSourceSnapshot selectRecentSnapshot(@Param("sourceUrl") String sourceUrl, @Param("since") Date since);

    /**
     * 删除最近一次看到早于 cutoff、且不再被当前字段来源引用的存档
     */
    public int purgeSnapshots(@Param("cutoff") Date cutoff);

    public int insertHistory(AbrFieldHistory history);

    public List<AbrFieldHistory> selectHistory(@Param("tableName") String tableName, @Param("recordId") Long recordId);

    public AbrFieldHistory selectHistoryById(Long historyId);

    public int markRolledBack(Long historyId);
}
