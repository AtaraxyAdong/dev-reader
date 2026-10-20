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
