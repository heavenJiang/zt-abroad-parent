package com.ygb.abroad.service;

import org.springframework.web.multipart.MultipartFile;
import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppResumeParseVo;

/**
 * 简历 / 成绩单上传与解析 服务层（C 端：测评经历页“上传简历，自动帮你填”）
 * 
 * 所有方法都按“本人”取数：测评必须属于传入的访客，否则视为不存在。识别结果只是建议值，不写入答案。
 * 
 * @author ygb
 */
public interface IAbrResumeService
{
    /**
     * 上传简历或成绩单并开始识别（同一类别再次上传会替换上一份并删除其原件）
     * 
     * @param visitor 访客
     * @param assessmentId 测评ID
     * @param kind 类别（resume / transcript）
     * @param consent 是否已勾选单独同意
     * @param file 文件（PDF / 图片）
     * @return 识别结果（新文件为识别中）
     */
    public AppResumeParseVo uploadResume(AbrVisitor visitor, Long assessmentId, String kind, Boolean consent, MultipartFile file);

    /**
     * 取识别结果（轮询；识别中超时的改为未识别）
     * 
     * @param visitor 访客
     * @param assessmentId 测评ID
     * @return 识别结果
     */
    public AppResumeParseVo selectResumeParse(AbrVisitor visitor, Long assessmentId);

    /**
     * 删除一份上传文件（原件立即删除，已确认写入的答案不受影响）
     * 
     * @param visitor 访客
     * @param assessmentId 测评ID
     * @param fileId 文件ID
     * @return 删除后的识别结果
     */
    public AppResumeParseVo deleteResume(AbrVisitor visitor, Long assessmentId, Long fileId);

    /**
     * 删除到期的原件（定时任务调用）
     * 
     * @return 删除的份数
     */
    public int purgeExpiredResumes();
}
