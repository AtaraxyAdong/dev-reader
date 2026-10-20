package com.github.yuyuanweb.mianshiyaplugin.reader;

import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;
import java.net.URI;
import static org.junit.Assert.*;

public class QuestionHtmlBuilderTest {
    private final URI uri = URI.create("https://www.mianshiya.com/question/123");

    @Test public void preservesReadableBodyWhenAnswerIsForbidden() {
        Document html = build("title", new ContentPart(PartStatus.AVAILABLE, SourceFormat.MARKDOWN, "**body text**", uri),
                new ContentPart(PartStatus.FORBIDDEN, SourceFormat.UNKNOWN, "", uri));
        assertEquals("body text", html.select("section[data-section=body] strong").text());
        assertFalse(html.select("section[data-section=answer] .content-status").text().isEmpty());
        assertFalse(html.text().contains("暂无参考答案"));
    }

    @Test public void rendersUntrustedTitleAndUnknownFormatAsText() {
        String hostile = "<img src=x onerror=alert(1)>";
        Document html = build(hostile, new ContentPart(PartStatus.AVAILABLE, SourceFormat.UNKNOWN, hostile, uri), ContentPart.unavailable(uri));
        assertEquals(hostile, html.selectFirst("h1").text());
        assertTrue(html.select("img,script,[onerror]").isEmpty());
        assertEquals(hostile, html.selectFirst("pre").text());
    }

    @Test public void distinguishesMissingAnswerFromEmptyAnswer() {
        ContentPart body = new ContentPart(PartStatus.EMPTY, SourceFormat.UNKNOWN, "", uri);
        String missing = build("title", body, ContentPart.unavailable(uri)).text();
        String empty = build("title", body, new ContentPart(PartStatus.EMPTY, SourceFormat.UNKNOWN, "", uri)).text();
        assertFalse(missing.contains("暂无参考答案"));
        assertTrue(empty.contains("暂无参考答案"));
    }

    private Document build(String title, ContentPart body, ContentPart answer) {
        return Jsoup.parseBodyFragment(QuestionHtmlBuilder.render(new QuestionDocument(123, title, body, answer, uri)).getHtml());
    }
}
