package com.ygb.abroad.service;

import java.io.InputStream;
import java.util.Map;

/**
 * 项目批量导入 服务层（预览 + 确认两步）
 *
 * @author ygb
 */
public interface IAbrProgramImportService
{
    /**
     * 解析 Excel 并逐行校验，结果暂存 30 分钟供确认
     *
     * @param is Excel 文件流
     * @return token、rows（每行的处理结果与信息）、summary（各结果的行数）
     */
    public Map<String, Object> preview(InputStream is);

    /**
     * 按预览结果导入：重新校验后新建院校、项目与申请要求，有错误或重复的行跳过
     *
     * @param token 预览返回的 token
     * @param operator 操作人
     * @return 新建院校数、新建项目数、新增申请要求数、跳过行数
     */
    public Map<String, Object> confirm(String token, String operator);
}
