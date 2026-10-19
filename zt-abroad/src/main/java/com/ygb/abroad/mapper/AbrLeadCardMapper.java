package com.ygb.abroad.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrLeadCard;

/**
 * 线索卡片 数据层（卡片读写 + 生成卡片用的线索数据）
 *
 * @author ygb
 */
public interface AbrLeadCardMapper
{
    /**
     * 线索的卡片
     *
     * @param leadId 线索ID
     * @return 卡片，没有为 null
     */
    public AbrLeadCard selectByLead(Long leadId);

    /**
     * 写入或覆盖卡片
     *
     * @param card 卡片
     * @return 结果
     */
    public int upsertCard(AbrLeadCard card);

    /**
     * 主访客最近一次测评结果的各档数量与项目
     *
     * @param visitorId 访客ID
     * @return [reach, match, safety, needsImprovement, programs]；没有为 null
     */
    public Map<String, Object> selectLatestResult(Long visitorId);

    /**
     * 主访客最近一次归一化后的背景档案
     *
     * @param visitorId 访客ID
     * @return 档案；没有为 null
     */
    public Map<String, Object> selectLatestProfile(Long visitorId);

    /**
     * 选校单中的项目（院校 + 项目名，最多 8 个）
     *
     * @param visitorId 访客ID
     * @return 项目
     */
    public List<Map<String, Object>> selectPlanPrograms(Long visitorId);

    /**
     * 各工具的使用次数
     *
     * @param visitorId 访客ID
     * @return [tool, count]
     */
    public List<Map<String, Object>> selectToolCounts(Long visitorId);

    /**
     * 发生过的转化时刻
     *
     * @param visitorId 访客ID
     * @return 时刻编码
     */
    public List<String> selectMoments(Long visitorId);

    /**
     * 项目的院校与项目名
     *
     * @param programIds 项目ID
     * @return [programId, school, program]
     */
    public List<Map<String, Object>> selectProgramNames(@Param("programIds") List<Long> programIds);
}
