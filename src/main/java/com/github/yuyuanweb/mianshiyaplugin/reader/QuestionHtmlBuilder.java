package com.github.yuyuanweb.mianshiyaplugin.reader;

import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.*;
import com.github.yuyuanweb.mianshiyaplugin.reader.render.ContentNormalizer;
import com.github.yuyuanweb.mianshiyaplugin.reader.render.MarkdownRenderer;
import org.jsoup.nodes.Element;

/** Builds a document from independent parts without turning access errors into empty answers. */
public final class QuestionHtmlBuilder {
    private QuestionHtmlBuilder() { }

    public static RenderedQuestion render(QuestionDocument document) {
        Element article = new Element("article");
        article.appendElement("h1").text(document.getTitle());
        appendPart(article, "body", ReaderMessages.text("body"), document.getBody());
        Element answer = article.appendElement("details").attr("id", "reference-answer").attr("open", "");
        answer.appendElement("summary").text(ReaderMessages.text("answer"));
        appendPart(answer, "answer", null, document.getReferenceAnswer());
        return new RenderedQuestion(document.getId(), article.outerHtml(), document.getSourceUri());
    }

    private static void appendPart(Element parent, String id, String heading, ContentPart part) {
        Element section = parent.appendElement("section").attr("data-section", id);
        if (heading != null) section.appendElement("h2").text(heading);
        if (part.getStatus() == PartStatus.AVAILABLE && part.getFormat() != SourceFormat.UNKNOWN) {
            section.append(MarkdownRenderer.renderFragment(ContentNormalizer.toMarkdown(part)));
            return;
        }
        String key;
        switch (part.getStatus()) {
            case EMPTY: key = id.equals("body") ? "body.empty" : "part.empty"; break;
            case FORBIDDEN: key = "part.forbidden"; break;
            case ERROR: key = "part.error"; break;
            case AVAILABLE: key = "part.format"; break;
            default: key = "part.unavailable";
        }
        section.appendElement("p").addClass("content-status").text(ReaderMessages.text(key));
        if (part.getStatus() == PartStatus.AVAILABLE && !part.getContent().isEmpty()) {
            section.appendElement("pre").text(part.getContent());
        }
    }
}
