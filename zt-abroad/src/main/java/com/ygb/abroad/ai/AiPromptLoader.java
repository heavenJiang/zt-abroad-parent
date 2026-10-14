package com.ygb.abroad.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 从 classpath 的 prompts/ 目录加载提示词、输出 Schema、mock 输出、评测样例与过滤词表
 * <p>
 * 目录结构：prompts/&lt;agentCode&gt;/&lt;version&gt;/{system.md, user.md, schema.json, mock.json, eval/*.json}；
 * 过滤词表：prompts/_filters/*.txt。提示词按 Agent + 版本缓存（打包在 jar 内，运行期不变）。
 * </p>
 *
 * @author ygb
 */
@Component
public class AiPromptLoader
{
    /** 根目录 */
    public static final String ROOT = "prompts/";

    /** Agent 编码与版本只允许字母、数字、下划线、中划线（防止路径穿越） */
    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9_\\-]{1,64}$");

    private final Map<String, AiPrompt> cache = new ConcurrentHashMap<>();

    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(AiPromptLoader.class.getClassLoader());

    /**
     * 加载提示词
     *
     * @param agentCode Agent 编码
     * @param version 版本
     * @return 提示词
     * @throws AiException 目录不存在或缺少必需文件
     */
    public AiPrompt load(String agentCode, String version)
    {
        String dir = dir(agentCode, version);
        return cache.computeIfAbsent(dir, d -> {
            String system = read(d + "system.md");
            String user = read(d + "user.md");
            String schemaText = read(d + "schema.json");
            if (system == null || user == null || schemaText == null)
            {
                throw new AiException("Agent「" + agentCode + "」的提示词目录 " + d + " 不存在或不完整（需要 system.md、user.md、schema.json）");
            }
            JsonNode schema = AiJson.parseOrNull(schemaText);
            if (schema == null || !schema.isObject())
            {
                throw new AiException("Agent「" + agentCode + "」的 " + d + "schema.json 不是合法的 JSON 对象");
            }
            String mockText = read(d + "mock.json");
            JsonNode mock = mockText == null ? null : AiJson.parseOrNull(mockText);
            if (mockText != null && mock == null)
            {
                throw new AiException("Agent「" + agentCode + "」的 " + d + "mock.json 不是合法的 JSON");
            }
            return new AiPrompt(agentCode, version, system, user, schema, mock);
        });
    }

    /**
     * 提示词目录是否完整
     *
     * @param agentCode Agent 编码
     * @param version 版本
     * @return true 存在 system.md、user.md、schema.json
     */
    public boolean exists(String agentCode, String version)
    {
        try
        {
            load(agentCode, version);
            return true;
        }
        catch (AiException e)
        {
            return false;
        }
    }

    /**
     * 读取评测样例（eval/*.json，按文件名排序）
     *
     * @param agentCode Agent 编码
     * @param version 版本
     * @return 文件名 → JSON
     */
    public List<Map.Entry<String, JsonNode>> loadEvalCases(String agentCode, String version)
    {
        String dir = dir(agentCode, version);
        List<Map.Entry<String, JsonNode>> result = new ArrayList<>();
        for (Resource r : resources("classpath*:" + dir + "eval/*.json"))
        {
            String text = readResource(r);
            JsonNode node = AiJson.parseOrNull(text);
            if (node == null || !node.isObject())
            {
                throw new AiException("评测样例 " + r.getFilename() + " 不是合法的 JSON 对象");
            }
            result.add(Map.entry(r.getFilename(), node));
        }
        return result;
    }

    /**
     * 读取过滤词表 prompts/_filters/*.txt
     *
     * @return 文件名（不含 .txt）→ 文本，按文件名排序
     */
    public List<Map.Entry<String, String>> loadFilterLists()
    {
        List<Map.Entry<String, String>> result = new ArrayList<>();
        for (Resource r : resources("classpath*:" + ROOT + "_filters/*.txt"))
        {
            String name = r.getFilename();
            result.add(Map.entry(name.substring(0, name.length() - 4), readResource(r)));
        }
        return result;
    }

    private List<Resource> resources(String pattern)
    {
        try
        {
            List<Resource> list = new ArrayList<>(Arrays.asList(resolver.getResources(pattern)));
            list.sort(Comparator.comparing(r -> String.valueOf(r.getFilename())));
            return list;
        }
        catch (IOException e)
        {
            throw new AiException("读取 " + pattern + " 失败：" + e.getMessage(), e);
        }
    }

    private static String dir(String agentCode, String version)
    {
        if (agentCode == null || version == null || !SAFE_NAME.matcher(agentCode).matches() || !SAFE_NAME.matcher(version).matches())
        {
            throw new AiException("Agent 编码或提示词版本不合法：" + agentCode + " / " + version);
        }
        return ROOT + agentCode + "/" + version + "/";
    }

    private static String read(String path)
    {
        try (InputStream in = AiPromptLoader.class.getClassLoader().getResourceAsStream(path))
        {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new AiException("读取 " + path + " 失败：" + e.getMessage(), e);
        }
    }

    private static String readResource(Resource r)
    {
        try (InputStream in = r.getInputStream())
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new AiException("读取 " + r.getFilename() + " 失败：" + e.getMessage(), e);
        }
    }
}
