package com.ygb.abroad.service.impl;

import java.util.List;
import java.util.Locale;

/**
 * 按项目英文名关键词归类学科大类与专业方向（纯计算，M2-08；字典 abr_field_group、abr_major_direction）
 * <p>
 * 按顺序匹配，先匹配更具体的（如 data science 在 science 之前、business analytics 在 analytics 之前）；都不匹配时大类为人文社科、方向为空，
 * 由大模型或员工补。
 * </p>
 *
 * @author ygb
 */
final class AbrProgramClassifier
{
    /**
     * 归类结果
     *
     * @param fieldGroup 学科大类
     * @param majorDirection 专业方向（不确定为 null）
     * @param matched 是否命中关键词
     */
    record Result(String fieldGroup, String majorDirection, boolean matched)
    {
    }

    private record Rule(String direction, String group, List<String> words)
    {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("business_analytics", "business", List.of("business analytics", "marketing analytics")),
            new Rule("data_ai", "stem", List.of("data science", "artificial intelligence", "machine learning", "data analytics", "ai", "big data", "data engineering")),
            new Rule("finance", "business", List.of("finance", "financial", "banking", "fintech", "investment", "quantitative", "actuarial")),
            new Rule("accounting", "business", List.of("accounting", "accountancy", "audit")),
            new Rule("economics", "business", List.of("economics", "econometrics")),
            new Rule("ee", "stem", List.of("electrical", "electronic", "communication engineering", "communications engineering", "telecommunication",
                    "signal processing", "microelectronics", "photonics", "power systems")),
            new Rule("cs", "stem", List.of("computer science", "computing", "software", "cyber", "information technology", "informatics", "computer",
                    "information systems", "human-computer", "robotics and ai")),
            new Rule("engineering", "stem", List.of("engineering", "mechanical", "civil", "chemical", "aerospace", "materials", "robotics", "structural",
                    "manufacturing")),
            new Rule("science", "stem", List.of("mathematics", "statistics", "physics", "chemistry", "biology", "biochemistry", "neuroscience",
                    "environmental science", "geoscience", "mathematical", "science")),
            new Rule("health", "health", List.of("public health", "medicine", "medical", "nursing", "pharmacy", "pharmacology", "health", "clinical",
                    "epidemiology", "dental", "nutrition", "biomedical")),
            new Rule("management", "business", List.of("management", "marketing", "mba", "business administration", "human resource", "supply chain",
                    "logistics", "entrepreneurship", "international business", "business", "innovation", "operations")),
            new Rule("law", "humanities_social", List.of("law", "llm", "legal")),
            new Rule("education", "humanities_social", List.of("education", "tesol", "teaching", "applied linguistics and tesol")),
            new Rule("media", "humanities_social", List.of("media", "journalism", "communication", "communications", "public relations", "advertising")),
            new Rule("social_science", "humanities_social", List.of("international relations", "politics", "public policy", "public administration",
                    "sociology", "development", "psychology", "anthropology", "social", "security studies", "global", "criminology", "urban")),
            new Rule("humanities", "humanities_social", List.of("translation", "interpreting", "linguistics", "literature", "history", "philosophy",
                    "english", "languages", "classics", "theology", "religion", "cultural")),
            new Rule("arts_design", "arts", List.of("design", "art", "architecture", "music", "film", "fashion", "photography", "theatre", "fine art")));

    private AbrProgramClassifier()
    {
    }

    static Result classify(String nameEn)
    {
        String n = " " + (nameEn == null ? "" : nameEn.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ")).trim() + " ";
        for (Rule r : RULES)
        {
            for (String w : r.words())
            {
                String word = w.trim();
                // 整词匹配（允许复数 s）
                if (n.contains(" " + word + " ") || n.contains(" " + word + "s "))
                {
                    return new Result(r.group(), r.direction(), true);
                }
            }
        }
        return new Result("humanities_social", null, false);
    }
}
