package com.ygb.abroad.service;

import java.util.Map;

/**
 * 项目 AI 预填（catalog_extractor）服务层
 * 
 * @author ygb
 */
public interface IAbrProgramAiPrefillService
{
    /**
     * 抓取官网页面，经 AI 网关抽取字段并逐字段核对引句；只返回提议值，不写任何项目、申请要求表
     * 
     * @param url 项目官网页面链接
     * @param programId 项目ID（可为空）
     * @param operator 操作人
     * @return {url, requestUrl, pageTitle, truncated, fetchedAt, elapsedMs, callId, summary, fields:[{fieldName, label, target, value, quote, status, reason}]}
     */
    public Map<String, Object> prefill(String url, Long programId, String operator);
}
