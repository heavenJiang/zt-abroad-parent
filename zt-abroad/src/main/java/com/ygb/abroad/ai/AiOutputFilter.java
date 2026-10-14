package com.ygb.abroad.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * 输出过滤：录取承诺、广告违禁词、整篇文书
 * <p>
 * 词表在 prompts/_filters/*.txt（文件名为类别编码），每行一个词；# 开头为注释，
 * “# 类别：xxx” 指定类别名称；! 开头为例外短语（匹配前先从文本中去掉，如 !第一年）。
 * 匹配时忽略大小写与空白。任何一个字符串字段超过最大长度视为整篇文书。
 * </p>
 *
 * @author ygb
 */
public class AiOutputFilter
{
    /** 整篇文书类别 */
    public static final String TYPE_ESSAY = "essay";

    /** 默认单个字符串字段最大长度 */
    public static final int DEFAULT_MAX_FIELD_LENGTH = 1500;

    private final List<WordList> lists = new ArrayList<>();

    private final int maxFieldLength;

    /**
     * 创建过滤器
     *
     * @param files 类别编码 → 词表文本
     * @param maxFieldLength 单个字符串字段最大长度（&lt;= 0 用默认 1500）
     */
    public AiOutputFilter(List<Map.Entry<String, String>> files, int maxFieldLength)
    {
        this.maxFieldLength = maxFieldLength > 0 ? maxFieldLength : DEFAULT_MAX_FIELD_LENGTH;
        for (Map.Entry<String, String> file : files)
        {
            lists.add(WordList.parse(file.getKey(), file.getValue()));
        }
    }

    /**
     * 检查输出
     *
     * @param output 模型输出
     * @return 命中项，为空表示通过
     */
    public List<AiFilterHit> check(JsonNode output)
    {
        List<AiFilterHit> hits = new ArrayList<>();
        if (output != null)
        {
            walk(output, "", hits);
        }
        return hits;
    }

    /**
     * 单个字符串字段最大长度
     *
     * @return 长度
     */
    public int getMaxFieldLength()
    {
        return maxFieldLength;
    }

    private void walk(JsonNode node, String path, List<AiFilterHit> hits)
    {
        if (node.isString())
        {
            checkText(node.asString(), path.isEmpty() ? "/" : path, hits);
        }
        else if (node.isObject())
        {
            for (Map.Entry<String, JsonNode> e : node.properties())
            {
                walk(e.getValue(), path + "/" + e.getKey(), hits);
            }
        }
        else if (node.isArray())
        {
            for (int i = 0; i < node.size(); i++)
            {
                walk(node.get(i), path + "/" + i, hits);
            }
        }
    }

    private void checkText(String text, String path, List<AiFilterHit> hits)
    {
        int len = text.codePointCount(0, text.length());
        if (len > maxFieldLength)
        {
            hits.add(new AiFilterHit(TYPE_ESSAY, "整篇文书", "字段长度 " + len + " 超过 " + maxFieldLength, path));
        }
        String normalized = normalize(text);
        for (WordList list : lists)
        {
            String s = normalized;
            for (String allowed : list.allowed)
            {
                s = s.replace(allowed, "");
            }
            for (String word : list.words)
            {
                if (s.contains(word))
                {
                    hits.add(new AiFilterHit(list.type, list.label, word, path));
                }
            }
        }
    }

    /**
     * 规范化：去空白、转小写
     */
    static String normalize(String text)
    {
        StringBuilder sb = new StringBuilder(text.length());
        text.codePoints().filter(c -> !Character.isWhitespace(c) && c != '　').forEach(sb::appendCodePoint);
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * 一个类别的词表
     */
    private static class WordList
    {
        private String type;

        private String label;

        private final Set<String> words = new LinkedHashSet<>();

        private final Set<String> allowed = new LinkedHashSet<>();

        static WordList parse(String type, String text)
        {
            WordList list = new WordList();
            list.type = type;
            list.label = type;
            for (String raw : text.split("\\r?\\n"))
            {
                String line = raw.trim();
                if (line.isEmpty())
                {
                    continue;
                }
                if (line.startsWith("#"))
                {
                    String c = line.substring(1).trim();
                    if (c.startsWith("类别：") || c.startsWith("类别:"))
                    {
                        list.label = c.substring(3).trim();
                    }
                    continue;
                }
                if (line.startsWith("!"))
                {
                    String a = normalize(line.substring(1));
                    if (!a.isEmpty())
                    {
                        list.allowed.add(a);
                    }
                    continue;
                }
                list.words.add(normalize(line));
            }
            return list;
        }
    }
}
