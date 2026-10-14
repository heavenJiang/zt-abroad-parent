package com.ygb.abroad.engine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 引擎测试用的种子读取（问卷 v1、各国引擎参数草案）
 *
 * @author ygb
 */
final class EngineSeeds
{
    static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private EngineSeeds()
    {
    }

    static JsonNode read(String... path)
    {
        try
        {
            return MAPPER.readTree(Files.readString(Path.of("..", path), StandardCharsets.UTF_8));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    static JsonNode questionnaire()
    {
        return read("seed", "questionnaire", "questionnaire_v1.json");
    }

    static JsonNode config(String file)
    {
        return read("seed", "engine-config", file);
    }

    static JsonNode json(String text)
    {
        return MAPPER.readTree(text);
    }

    /**
     * 答案 JSON 对象 → 题目 id → 答案
     */
    static Map<String, JsonNode> answers(String text)
    {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        json(text).properties().forEach(e -> map.put(e.getKey(), e.getValue()));
        return map;
    }
}
