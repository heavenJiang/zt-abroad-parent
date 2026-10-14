package com.ygb.abroad.integration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML 转可读纯文本（不引入第三方解析库）
 * <p>
 * 去掉注释、script / style / noscript / template / svg / iframe / select，以及导航 nav、页脚 footer 的全部内容；
 * 块级标签（p、div、li、h1–h6、tr、br…）转为换行；解码常见实体；每行压缩空白、去掉空行。
 * </p>
 *
 * @author ygb
 */
public final class AbrHtmlText
{
    /** 连同内容整体丢弃的标签（内容为原始文本，直接跳到结束标签） */
    private static final Set<String> RAW_SKIP = Set.of("script", "style", "textarea", "xmp");

    /** 连同内容整体丢弃的容器标签（支持嵌套） */
    private static final Set<String> CONTAINER_SKIP = Set.of("nav", "footer", "noscript", "template", "svg", "iframe", "select", "object");

    /** 转为换行的块级标签 */
    private static final Set<String> BLOCK = Set.of("p", "div", "br", "li", "ul", "ol", "dl", "dt", "dd", "h1", "h2", "h3", "h4", "h5",
            "h6", "tr", "table", "thead", "tbody", "tfoot", "caption", "section", "article", "header", "main", "aside", "blockquote",
            "pre", "form", "fieldset", "legend", "figure", "figcaption", "hr", "address", "title", "summary", "details", "body", "html",
            "option", "label");

    /** 表格单元格：转为空格 */
    private static final Set<String> CELL = Set.of("td", "th");

    /** 实体 */
    private static final Pattern ENTITY = Pattern.compile("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,31});");

    /** 标题 */
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 常见命名实体 */
    private static final Map<String, String> NAMED = new HashMap<>();

    static
    {
        String[][] pairs = { { "amp", "&" }, { "lt", "<" }, { "gt", ">" }, { "quot", "\"" }, { "apos", "'" }, { "nbsp", " " },
                { "ensp", " " }, { "emsp", " " }, { "thinsp", " " }, { "ndash", "–" }, { "mdash", "—" }, { "minus", "−" },
                { "lsquo", "‘" }, { "rsquo", "’" }, { "sbquo", "‚" }, { "ldquo", "“" }, { "rdquo", "”" }, { "bdquo", "„" },
                { "hellip", "…" }, { "bull", "•" }, { "middot", "·" }, { "pound", "£" }, { "euro", "€" }, { "yen", "¥" },
                { "cent", "¢" }, { "dollar", "$" }, { "copy", "©" }, { "reg", "®" }, { "trade", "™" }, { "deg", "°" },
                { "times", "×" }, { "divide", "÷" }, { "plusmn", "±" }, { "frac12", "½" }, { "frac14", "¼" }, { "frac34", "¾" },
                { "laquo", "«" }, { "raquo", "»" }, { "sect", "§" }, { "para", "¶" }, { "percnt", "%" }, { "comma", "," },
                { "period", "." }, { "colon", ":" }, { "semi", ";" }, { "lpar", "(" }, { "rpar", ")" }, { "sol", "/" },
                { "auml", "ä" }, { "ouml", "ö" }, { "uuml", "ü" }, { "Auml", "Ä" }, { "Ouml", "Ö" }, { "Uuml", "Ü" },
                { "szlig", "ß" }, { "eacute", "é" }, { "egrave", "è" }, { "aacute", "á" }, { "agrave", "à" }, { "ccedil", "ç" },
                { "iacute", "í" }, { "oacute", "ó" }, { "uacute", "ú" }, { "ntilde", "ñ" }, { "zwj", "" }, { "zwnj", "" },
                { "shy", "" } };
        for (String[] p : pairs)
        {
            NAMED.put(p[0], p[1]);
        }
    }

    private AbrHtmlText()
    {
    }

    /**
     * HTML 转纯文本
     *
     * @param html HTML 源码
     * @return 纯文本（行之间用 \n 分隔，无空行）
     */
    public static String toText(String html)
    {
        if (html == null || html.isEmpty())
        {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.min(html.length(), 1 << 20));
        Map<String, Integer> open = new HashMap<>();
        int skipDepth = 0;
        int n = html.length();
        int i = 0;
        while (i < n)
        {
            int lt = html.indexOf('<', i);
            if (lt < 0)
            {
                if (skipDepth == 0)
                {
                    out.append(html, i, n);
                }
                break;
            }
            if (lt > i && skipDepth == 0)
            {
                out.append(html, i, lt);
            }
            if (html.startsWith("<!--", lt))
            {
                int end = html.indexOf("-->", lt + 4);
                i = end < 0 ? n : end + 3;
                continue;
            }
            if (lt + 1 < n && (html.charAt(lt + 1) == '!' || html.charAt(lt + 1) == '?'))
            {
                int end = html.indexOf('>', lt + 2);
                i = end < 0 ? n : end + 1;
                continue;
            }
            int p = lt + 1;
            boolean closing = p < n && html.charAt(p) == '/';
            if (closing)
            {
                p++;
            }
            int nameStart = p;
            while (p < n && (Character.isLetterOrDigit(html.charAt(p)) || html.charAt(p) == '-' || html.charAt(p) == ':'))
            {
                p++;
            }
            if (p == nameStart || !Character.isLetter(html.charAt(nameStart)))
            {
                // 不是标签（如 “a < b”），按文本处理
                if (skipDepth == 0)
                {
                    out.append('<');
                }
                i = lt + 1;
                continue;
            }
            String name = html.substring(nameStart, p).toLowerCase(Locale.ROOT);
            int gt = tagEnd(html, p);
            boolean selfClosing = gt > 0 && html.charAt(gt - 1) == '/';
            i = gt < 0 ? n : gt + 1;
            if (!closing && RAW_SKIP.contains(name))
            {
                if (!selfClosing)
                {
                    int end = indexOfIgnoreCase(html, "</" + name, i);
                    if (end < 0)
                    {
                        i = n;
                    }
                    else
                    {
                        int endGt = html.indexOf('>', end);
                        i = endGt < 0 ? n : endGt + 1;
                    }
                }
                continue;
            }
            if (CONTAINER_SKIP.contains(name))
            {
                if (closing)
                {
                    int c = open.getOrDefault(name, 0);
                    if (c > 0)
                    {
                        open.put(name, c - 1);
                        skipDepth--;
                    }
                }
                else if (!selfClosing)
                {
                    open.merge(name, 1, Integer::sum);
                    skipDepth++;
                }
                continue;
            }
            if (skipDepth > 0)
            {
                continue;
            }
            if (BLOCK.contains(name))
            {
                out.append('\n');
            }
            else if (CELL.contains(name))
            {
                out.append(' ');
            }
        }
        return normalize(decodeEntities(out.toString()));
    }

    /**
     * 纯文本规范化：每行压缩空白并去掉首尾空白，去掉空行
     *
     * @param text 文本
     * @return 规范化后的文本
     */
    public static String normalize(String text)
    {
        if (text == null)
        {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (String raw : text.replace("\r\n", "\n").replace('\r', '\n').split("\n"))
        {
            String line = collapse(raw);
            if (!line.isEmpty())
            {
                if (sb.length() > 0)
                {
                    sb.append('\n');
                }
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /**
     * 取页面标题
     *
     * @param html HTML 源码
     * @return 标题（无则空串）
     */
    public static String title(String html)
    {
        if (html == null)
        {
            return "";
        }
        Matcher m = TITLE.matcher(html);
        return m.find() ? collapse(decodeEntities(m.group(1).replaceAll("<[^>]*>", " "))) : "";
    }

    /**
     * 解码实体：常见命名实体与数字实体；未知实体原样保留
     *
     * @param text 文本
     * @return 解码后的文本
     */
    public static String decodeEntities(String text)
    {
        if (text == null || text.indexOf('&') < 0)
        {
            return text;
        }
        Matcher m = ENTITY.matcher(text);
        StringBuilder sb = new StringBuilder(text.length());
        while (m.find())
        {
            String e = m.group(1);
            String replacement = null;
            if (e.charAt(0) == '#')
            {
                try
                {
                    int cp = e.length() > 1 && (e.charAt(1) == 'x' || e.charAt(1) == 'X') ? Integer.parseInt(e.substring(2), 16)
                            : Integer.parseInt(e.substring(1));
                    if (cp == 0xA0)
                    {
                        replacement = " ";
                    }
                    else if (Character.isValidCodePoint(cp) && cp != 0 && (cp < 0xD800 || cp > 0xDFFF))
                    {
                        replacement = new String(Character.toChars(cp));
                    }
                }
                catch (NumberFormatException ex)
                {
                    replacement = null;
                }
            }
            else
            {
                replacement = NAMED.get(e);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement == null ? m.group() : replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 压缩一行中的空白（含不换行空格、全角空格、零宽字符）
     */
    private static String collapse(String line)
    {
        StringBuilder sb = new StringBuilder(line.length());
        boolean space = false;
        for (int k = 0; k < line.length(); k++)
        {
            char c = line.charAt(k);
            if (c == '​' || c == '‌' || c == '‍' || c == '﻿' || c == '­')
            {
                continue;
            }
            if (Character.isWhitespace(c) || c == ' ' || c == ' ' || c == ' ')
            {
                space = true;
                continue;
            }
            if (space && sb.length() > 0)
            {
                sb.append(' ');
            }
            space = false;
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * 标签结束位置（跳过属性值中的引号内容）
     */
    private static int tagEnd(String html, int from)
    {
        char quote = 0;
        char prev = 0;
        for (int k = from; k < html.length(); k++)
        {
            char c = html.charAt(k);
            if (quote != 0)
            {
                if (c == quote)
                {
                    quote = 0;
                }
            }
            else if ((c == '"' || c == '\'') && prev == '=')
            {
                // 只有紧跟在 = 后的引号才是属性值的开始（避免 title=O'Brien 吞掉后文）
                quote = c;
            }
            else if (c == '>')
            {
                return k;
            }
            if (!Character.isWhitespace(c))
            {
                prev = c;
            }
        }
        return -1;
    }

    private static int indexOfIgnoreCase(String s, String sub, int from)
    {
        int max = s.length() - sub.length();
        for (int k = Math.max(0, from); k <= max; k++)
        {
            if (s.regionMatches(true, k, sub, 0, sub.length()))
            {
                return k;
            }
        }
        return -1;
    }
}
