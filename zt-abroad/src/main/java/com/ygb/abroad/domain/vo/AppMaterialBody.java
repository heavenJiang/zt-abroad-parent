package com.ygb.abroad.domain.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * C 端：勾选材料完成状态的请求体
 *
 * @author ygb
 */
public class AppMaterialBody
{
    /** 材料键（来自材料清单） */
    private String key;

    /** 是否完成 */
    private Boolean done;

    @NotBlank(message = "缺少材料")
    @Size(max = 64, message = "材料不正确")
    public String getKey()
    {
        return key;
    }

    public void setKey(String key)
    {
        this.key = key;
    }

    @NotNull(message = "缺少完成状态")
    public Boolean getDone()
    {
        return done;
    }

    public void setDone(Boolean done)
    {
        this.done = done;
    }
}
