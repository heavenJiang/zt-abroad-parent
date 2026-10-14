package com.ygb.abroad.domain.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * C 端：分步保存答案的请求体
 * 
 * @author ygb
 */
public class AppAnswersBody
{
    /** 本次改动的答案（题目 id → 答案，null 表示清除） */
    private JsonNode answers;

    /** 当前所在的屏 id（断点） */
    private String currentScreen;

    @NotNull(message = "没有要保存的答案")
    public JsonNode getAnswers()
    {
        return answers;
    }

    public void setAnswers(JsonNode answers)
    {
        this.answers = answers;
    }

    @Pattern(regexp = "^[a-z][a-z0-9_]{0,63}$", message = "屏 id 格式不正确")
    public String getCurrentScreen()
    {
        return currentScreen;
    }

    public void setCurrentScreen(String currentScreen)
    {
        this.currentScreen = currentScreen;
    }
}
