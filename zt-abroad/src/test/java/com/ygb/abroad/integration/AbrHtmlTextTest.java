package com.ygb.abroad.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * HTML 转纯文本测试
 *
 * @author ygb
 */
class AbrHtmlTextTest
{
    @Test
    void dropsScriptsStylesNavFooterAndComments()
    {
        String html = "<html><head><title>T</title><style>p{color:red}</style>"
                + "<script type=\"text/javascript\">if (a < b) { document.write('<p>IELTS 5.0</p>'); }</script></head>"
                + "<body><!-- hidden IELTS 4.0 --><nav class=\"menu\"><ul><li>Home</li><li><nav>Inner</nav>Courses</li></ul></nav>"
                + "<header><h1>MSc Data Science</h1></header>"
                + "<noscript>Enable JavaScript</noscript><svg><text>logo</text></svg>"
                + "<main><p>Entry requirements</p></main><footer><p>© 2027 University</p></footer><p>After footer</p></body></html>";
        String text = AbrHtmlText.toText(html);
        assertEquals("T\nMSc Data Science\nEntry requirements\nAfter footer", text);
    }

    @Test
    void blockTagsBecomeLinesAndInlineTagsJoin()
    {
        String html = "<div>IELTS <strong>7.0</strong> overall<br>no less than <em>6.5</em></div>"
                + "<ul><li>Round 1: 15 November 2026</li><li>Round 2: 15 January 2027</li></ul>"
                + "<table><tr><th>Fee</th><td>£38,500</td></tr></table>";
        assertEquals("IELTS 7.0 overall\nno less than 6.5\nRound 1: 15 November 2026\nRound 2: 15 January 2027\nFee £38,500",
                AbrHtmlText.toText(html));
    }

    @Test
    void decodesEntitiesAndCollapsesWhitespace()
    {
        String html = "<p>  Fees:&nbsp;&pound;38,500 &amp; &#8364;1,000 &#x2014; &lsquo;quoted&rsquo;&hellip; &unknown; </p>\n\n\n<p>\tA&lt;B</p>";
        assertEquals("Fees: £38,500 & €1,000 — ‘quoted’… &unknown;\nA<B", AbrHtmlText.toText(html));
        assertEquals("a & b", AbrHtmlText.decodeEntities("a &amp; b"));
        assertEquals("x&#0;y", AbrHtmlText.decodeEntities("x&#0;y"));
    }

    @Test
    void attributesWithGreaterThanAndQuotes()
    {
        String html = "<a href=\"/x?a>b\" title='it>s'>Apply now</a> <img alt=O'Brien src=x>text";
        assertEquals("Apply now text", AbrHtmlText.toText(html));
        assertEquals("a < b and c", AbrHtmlText.toText("a < b and c"));
    }

    @Test
    void titleAndEdgeCases()
    {
        assertEquals("MSc Finance | Uni", AbrHtmlText.title("<html><head><title>\n MSc Finance &#124; Uni </title></head></html>"));
        assertEquals("", AbrHtmlText.title("<p>no title</p>"));
        assertEquals("", AbrHtmlText.toText(null));
        assertEquals("", AbrHtmlText.toText("<script>unterminated"));
        assertEquals("tail", AbrHtmlText.toText("<!-- unterminated comment --><p>tail"));
        assertFalse(AbrHtmlText.toText("<select><option>UK</option></select>Text").contains("UK"));
        assertTrue(AbrHtmlText.normalize("a  b\r\n\r\n c ").equals("a b\nc"));
    }
}
