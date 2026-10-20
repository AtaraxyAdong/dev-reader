package com.github.yuyuanweb.mianshiyaplugin.reader.render;

import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.ContentPart;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.PartStatus;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.SourceFormat;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class ContentNormalizerTest {
    private static final URI BASE = URI.create("https://example.test/questions/42/");

    @Test
    public void preservesCodeAndLanguage() throws Exception {
        String markdown = ContentNormalizer.toMarkdown(part(SourceFormat.HTML, htmlFixture()));
        Document document = Jsoup.parseBodyFragment(MarkdownRenderer.renderFragment(markdown));
        assertTrue(document.select("pre code").text().contains("int answer = 42;"));
        assertTrue(document.select("pre code").text().contains("return answer;"));
        assertEquals(1, document.select("pre code[class*=language-]").size());
    }

    @Test
    public void resolvesRelativeImages() throws Exception {
        String markdown = ContentNormalizer.toMarkdown(part(SourceFormat.HTML, htmlFixture()));
        assertTrue(markdown.contains("https://example.test/questions/images/answer.png"));
        String rendered = MarkdownRenderer.renderFragment(markdown);
        assertEquals("https://example.test/questions/images/answer.png", Jsoup.parseBodyFragment(rendered).selectFirst("img").attr("src"));
    }

    @Test
    public void preservesTablesAndSemantics() throws Exception {
        String markdown = ContentNormalizer.toMarkdown(part(SourceFormat.HTML, htmlFixture()));
        Document document = Jsoup.parseBodyFragment(MarkdownRenderer.renderFragment(markdown));
        assertEquals(1, document.select("table").size());
        assertTrue(document.text().contains("项"));
        assertEquals(1, document.select("br").size());
        assertEquals(1, document.select("sub").size());
        assertEquals(1, document.select("sup").size());
    }

    @Test
    public void rendersMarkdownFixtureAsSemanticBlocks() throws Exception {
        String markdown = resource("/reader/render/sample.md");
        Document document = Jsoup.parseBodyFragment(MarkdownRenderer.renderFragment(
                ContentNormalizer.toMarkdown(part(SourceFormat.MARKDOWN, markdown))));
        assertEquals("中文题目", document.selectFirst("h1").text());
        assertEquals(2, document.select("ol > li").size());
        assertEquals(1, document.select("table").size());
        assertTrue(document.selectFirst("pre code").text().contains("return answer;"));
        assertFalse(document.select("[id^=block-]").isEmpty());
    }

    @Test
    public void resolvesMarkdownDestinationsWithoutTouchingCodeAndReferenceLinks() {
        String source = "Inline `[keep](../unchanged.png)` and [nested](../images/a_(b).png).\n\n"
                + "```text\n[also keep](../fenced.png)\n```\n\n"
                + "![image](../images/figure_(1).png)\n\n"
                + "[reference][asset] and [section](#section)\n\n[asset]: ../images/reference.png\n";
        String normalized = ContentNormalizer.toMarkdown(part(SourceFormat.MARKDOWN, source));
        assertTrue(normalized.contains("`[keep](../unchanged.png)`"));
        assertTrue(normalized.contains("[also keep](../fenced.png)"));
        assertTrue(normalized.contains("https://example.test/questions/images/a_(b).png"));
        assertTrue(normalized.contains("https://example.test/questions/images/figure_(1).png"));
        assertTrue(normalized.contains("[asset]: https://example.test/questions/images/reference.png"));
        assertTrue(normalized.contains("[section](#section)"));
        String rendered = MarkdownRenderer.renderFragment(normalized);
        assertEquals("#section", Jsoup.parseBodyFragment(rendered).selectFirst("a[href=#section]").attr("href"));
    }

    @Test
    public void removesWebsiteStylesAndActiveContent() throws Exception {
        String markdown = ContentNormalizer.toMarkdown(part(SourceFormat.HTML, htmlFixture()));
        String rendered = MarkdownRenderer.renderFragment(markdown);
        Document document = Jsoup.parseBodyFragment(rendered);
        assertTrue(document.select("script, style, iframe").isEmpty());
        assertTrue(document.select("[style], [class=site-card], [onclick]").isEmpty());
        assertTrue(document.select("a[href^=javascript]").isEmpty());
        assertTrue(document.text().contains("题目 & 说明"));
        assertFalse(rendered.toLowerCase().contains("stealcookies"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownFormat() {
        ContentNormalizer.toMarkdown(part(SourceFormat.UNKNOWN, "some payload"));
    }

    @Test
    public void rendersMarkdownMarkedAsHtmlWithoutFlatteningItsBlocks() {
        String source = "## Answer\n\nA short introduction.\n\n"
                + "<img src=\"https://example.test/diagram.png\" alt=\"diagram\"/>\n\n"
                + "## Extension\n\n### Cursor paging\n\n"
                + "1. **Unique** identifiers\n2. Stable ordering\n\n"
                + "```sql\nSELECT * FROM messages\nWHERE id < :cursor\n```\n\n"
                + "```json\n{\"nextCursor\": \"42\"}\n```\n";
        Document document = renderHtmlMetadata(source);
        assertEquals(2, document.select("h2").size());
        assertEquals("Cursor paging", document.selectFirst("h3").text());
        assertEquals(2, document.select("ol > li").size());
        assertEquals("Unique", document.selectFirst("li strong").text());
        assertEquals(1, document.select("img").size());
        assertEquals(2, document.select("pre code").size());
        assertTrue(document.selectFirst("code.language-sql").wholeText().contains("\nWHERE id < :cursor"));
        assertEquals("{\"nextCursor\": \"42\"}\n", document.selectFirst("code.language-json").wholeText());
    }

    @Test
    public void keepsHtmlParagraphsContainingLiteralMarkdownAsHtml() {
        Document document = renderHtmlMetadata("<div><h2>HTML heading</h2><p>##literal heading</p>"
                + "<p>**literal emphasis**</p><pre><code>## literal code heading</code></pre></div>");
        assertEquals(1, document.select("h2").size());
        assertEquals("##literal heading", document.select("p").get(0).text());
        assertEquals("**literal emphasis**", document.select("p").get(1).text());
        assertTrue(document.select("strong").isEmpty());
        assertEquals("## literal code heading", document.selectFirst("pre code").text());
    }

    @Test
    public void preservesHtmlExamplesInsideMislabelledMarkdownCode() {
        String source = "```html\n<div><img src=\"../example.png\" onerror=\"demo()\"></div>\n```\n\n"
                + "Inline `<script>demo()</script>` example.\n";
        Document document = renderHtmlMetadata(source);
        assertEquals("<div><img src=\"../example.png\" onerror=\"demo()\"></div>\n",
                document.selectFirst("pre code.language-html").wholeText());
        assertTrue(document.select("p code").text().contains("<script>demo()</script>"));
        assertTrue(document.select("img, script, [onerror]").isEmpty());
    }

    @Test
    public void sanitizesActiveHtmlInsideMislabelledMarkdown() {
        Document document = renderHtmlMetadata("## Answer\n\n<script>stealCookies()</script>\n\n"
                + "<img src=\"https://example.test/diagram.png\" onerror=\"bad()\" style=\"color:red\"/>\n\n"
                + "[unsafe](javascript:alert) and **safe text**\n");
        assertEquals(1, document.select("h2").size());
        assertEquals(1, document.select("img").size());
        assertTrue(document.select("script, [onerror], [style], a[href^=javascript]").isEmpty());
        assertFalse(document.text().contains("stealCookies"));
        assertEquals("safe text", document.selectFirst("strong").text());
    }

    private static Document renderHtmlMetadata(String source) {
        return Jsoup.parseBodyFragment(MarkdownRenderer.renderFragment(
                ContentNormalizer.toMarkdown(part(SourceFormat.HTML, source))));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnavailableContent() {
        ContentNormalizer.toMarkdown(ContentPart.unavailable(BASE));
    }

    private static ContentPart part(SourceFormat format, String content) {
        return new ContentPart(PartStatus.AVAILABLE, format, content, BASE);
    }

    private static String htmlFixture() throws IOException {
        return resource("/reader/render/rich-content.html");
    }

    private static String resource(String path) throws IOException {
        try (java.io.InputStream input = ContentNormalizerTest.class.getResourceAsStream(path)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
