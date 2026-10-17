package com.ygb.abroad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrAssessmentFile;

/**
 * 测评上传文件 数据层
 * 
 * @author ygb
 */
public interface AbrAssessmentFileMapper
{
    /**
     * 按ID查询文件（含已删除）
     * 
     * @param fileId 文件ID
     * @return 文件
     */
    public AbrAssessmentFile selectAbrAssessmentFileById(Long fileId);

    /**
     * 查询测评的有效文件（按上传时间）
     * 
     * @param assessmentId 测评ID
     * @return 文件集合
     */
    public List<AbrAssessmentFile> selectAbrAssessmentFileList(Long assessmentId);

    /**
     * 测评累计上传次数（含已删除、已替换，用于限制次数）
     * 
     * @param assessmentId 测评ID
     * @return 次数
     */
    public int countAbrAssessmentFileUploads(Long assessmentId);

    /**
     * 新增文件（状态为识别中）
     * 
     * @param file 文件
     * @return 影响行数
     */
    public int insertAbrAssessmentFile(AbrAssessmentFile file);

    /**
     * 标记删除（同时清空原件路径与原文件名；原件由调用方删除）
     * 
     * @param fileId 文件ID
     * @return 影响行数
     */
    public int deleteAbrAssessmentFileById(Long fileId);

    /**
     * 写回解析结果（令牌不一致或已不在识别中时不写）
     * 
     * @param file 结果（fileId、claimToken、parseStatus、textSource、textChars、parsed、failReason、aiStatus、callId、errorMsg）
     * @return 影响行数
     */
    public int finishAbrAssessmentFile(AbrAssessmentFile file);

    /**
     * 把识别中超时的文件改为未识别（超时）
     * 
     * @param assessmentId 测评ID
     * @param staleSeconds 识别中超过多少秒视为中断
     * @return 影响行数
     */
    public int failStaleAbrAssessmentFiles(@Param("assessmentId") Long assessmentId, @Param("staleSeconds") int staleSeconds);

    /**
     * 查询原件已到期、尚未删除的文件
     * 
     * @param limit 最多条数
     * @return 文件集合
     */
    public List<AbrAssessmentFile> selectExpiredAbrAssessmentFiles(@Param("limit") int limit);

    /**
     * 记录原件已删除（清空原件路径与原文件名）
     * 
     * @param fileId 文件ID
     * @return 影响行数
     */
    public int purgeAbrAssessmentFile(Long fileId);
}
