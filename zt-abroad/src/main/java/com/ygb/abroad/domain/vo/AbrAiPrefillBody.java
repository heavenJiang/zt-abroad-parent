package com.ygb.abroad.domain.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 项目 AI 预填的请求体
 * 
 * @author ygb
 */
public class AbrAiPrefillBody
{
    /** 项目官网页面链接 */
    private String url;

    /** 项目ID（可选：提供时把院校、项目名作为“目标项目”提示交给 AI） */
    private Long programId;

    @NotBlank(message = "请填写项目官网链接")
    @Size(max = 1000, message = "链接不能超过1000个字符")
    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    public Long getProgramId()
    {
        return programId;
    }

    public void setProgramId(Long programId)
    {
        this.programId = programId;
    }
}
