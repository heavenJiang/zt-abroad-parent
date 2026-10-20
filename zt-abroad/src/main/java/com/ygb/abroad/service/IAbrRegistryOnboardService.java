package com.ygb.abroad.service;

import java.util.Map;

/**
 * 第 1 批（英国、中国香港、新加坡、澳洲）学校按官方名录自动建档 服务层（M2-13）
 * 
 * @author ygb
 */
public interface IAbrRegistryOnboardService
{
    /**
     * 后台建立 / 更新第 1 批学校档案：下载并存档各国官方高校名录，校名须在名录中出现，官网以名录为准（名录有官网时）
     *
     * @param operator 操作人
     * @return 是否已开始（已有任务在运行时为 false）
     */
    public boolean start(String operator);

    /**
     * 最近一次建档的进度与结果
     */
    public Map<String, Object> status();
}
