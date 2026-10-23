package com.github.yuyuanweb.mianshiyaplugin.reader.render;

import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;

/** Renders Markdown as a small, sanitized HTML fragment for the local JCEF page. */
public final class MarkdownRenderer {
    private static final MutableDataSet OPTIONS = new MutableDataSet()
            .set(Parser.EXTENSIONS, Collections.singletonList(TablesExtension.create()));
    private static final Parser PARSER = Parser.builder(OPTIONS).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder(OPTIONS).build();
    private static final Safelist SAFE = new Safelist()
            .addTags("p", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "ul", "ol", "li",
                    "strong", "b", "em", "i", "del", "s", "br", "sub", "sup", "pre", "code",
                    "a", "img", "table", "thead", "tbody", "tfoot", "tr", "th", "td", "hr")
            .addAttributes("a", "href", "title")
            .addAttributes("img", "src", "alt", "title")
            .addAttributes("code", "class")
            .addAttributes("th", "colspan", "rowspan")
            .addAttributes("td", "colspan", "rowspan")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("a", "href", "#")
            .addProtocols("img", "src", "http", "https")
            .preserveRelativeLinks(true);

    private MarkdownRenderer() { }

    public static String renderFragment(String markdown) {
        if (markdown == null || markdown.isEmpty()) return "";
        Node ast = PARSER.parse(markdown);
        String rendered = RENDERER.render(ast);
        String clean = Jsoup.clean(rendered, "", SAFE, new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false));
        org.jsoup.nodes.Document fragment = Jsoup.parseBodyFragment(clean);
        int index = 0;
        for (org.jsoup.nodes.Element block : fragment.select("p, h1, h2, h3, h4, h5, h6, blockquote, ul, ol, pre, table, hr")) {
            String label = block.text().trim();
            if (label.isEmpty()) label = block.tagName();
            block.attr("id", "block-" + digest(label) + "-" + index++);
        }
        for (org.jsoup.nodes.Element code : fragment.select("code[class]")) {
            String value = code.className();
            if (!value.matches("language-[A-Za-z0-9_+-]{1,32}")) code.removeAttr("class");
        }
        return fragment.body().html();
    }

    private static String digest(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(12);
            for (int i = 0; i < 6; i++) value.append(String.format("%02x", bytes[i]));
            return value.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
