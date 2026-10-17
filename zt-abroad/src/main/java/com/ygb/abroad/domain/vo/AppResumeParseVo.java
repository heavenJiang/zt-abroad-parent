package com.ygb.abroad.domain.vo;

import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.databind.JsonNode;

/**
 * C 端：简历 / 成绩单识别结果（只是建议值，学生逐项确认后由前端按普通答案保存；内容为 AI 识别）
 *
 * @param status 整体状态（none 没有上传 / running 识别中，继续轮询 / done 至少一份已识别 / failed 全部未识别）
 * @param files 已上传的文件
 * @param suggestions 建议值（按问卷题目顺序）
 * @param schoolCandidates 本科学校候选（识别出的校名与高校库不完全一致时给出，供学生选择；已有学校建议时为空）
 * @author ygb
 */
public record AppResumeParseVo(String status, List<File> files, List<Suggestion> suggestions, List<AppCnUniversityVo> schoolCandidates)
{
    /** 整体状态：没有上传 */
    public static final String STATUS_NONE = "none";

    /** 与当前答案：当前未填 */
    public static final String MATCH_NEW = "new";

    /** 与当前答案：一致 */
    public static final String MATCH_SAME = "same";

    /** 与当前答案：不一致（并列显示让学生选，不自动覆盖） */
    public static final String MATCH_CONFLICT = "conflict";

    /**
     * 已上传的文件
     *
     * @param fileId 文件ID
     * @param kind 类别（resume 简历 / transcript 成绩单）
     * @param name 原文件名（原件到期删除后为空）
     * @param size 大小（字节）
     * @param status 状态（running 识别中 / done 已识别 / failed 未识别）
     * @param message 提示文字
     * @param uploadTime 上传时间
     */
    public record File(Long fileId, String kind, String name, Long size, String status, String message,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date uploadTime)
    {
    }

    /**
     * 一道题的建议值
     *
     * @param questionId 题目 id
     * @param value 建议值（与该题答案形状相同；经历题只含识别出的字段，“是否相关”等由学生补选）
     * @param current 当前答案（未填为 null）
     * @param match 与当前答案的关系（new / same / conflict）
     * @param source 来自哪份文件（resume / transcript）
     */
    public record Suggestion(String questionId, JsonNode value, JsonNode current, String match, String source)
    {
    }
}
