package com.github.yuyuanweb.mianshiyaplugin.reader.render;

import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.ContentPart;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.PartStatus;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.SourceFormat;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.html2md.converter.ExtensionConversion;
import com.vladsch.flexmark.ast.InlineLinkNode;
import com.vladsch.flexmark.ast.Reference;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.sequence.BasedSequence;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** Converts source content into safe, portable Markdown for the local reader. */
public final class ContentNormalizer {
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
            "p", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "ul", "ol", "li",
            "strong", "b", "em", "i", "del", "s", "br", "sub", "sup", "pre", "code",
            "a", "img", "table", "thead", "tbody", "tfoot", "tr", "th", "td", "hr", "div", "span"
    ));
    private static final Parser MARKDOWN_PARSER = Parser.builder().build();
    private static final MutableDataSet HTML_TO_MARKDOWN_OPTIONS = new MutableDataSet()
            .set(FlexmarkHtmlConverter.EXT_INLINE_SUB, ExtensionConversion.HTML)
            .set(FlexmarkHtmlConverter.EXT_INLINE_SUP, ExtensionConversion.HTML);
    private static final FlexmarkHtmlConverter HTML_CONVERTER = FlexmarkHtmlConverter.builder(HTML_TO_MARKDOWN_OPTIONS).build();

    private ContentNormalizer() { }

    public static String toMarkdown(ContentPart part) {
        if (part == null) throw new IllegalArgumentException("Content part is required");
        if (part.getStatus() != PartStatus.AVAILABLE) throw new IllegalArgumentException("Content part is not available: " + part.getStatus());
        if (part.getFormat() == SourceFormat.MARKDOWN) return resolveMarkdownUrls(part.getContent(), part.getBaseUri());
        if (part.getFormat() != SourceFormat.HTML) throw new IllegalArgumentException("Unsupported content format: " + part.getFormat());

        Document document = Jsoup.parseBodyFragment(part.getContent(), part.getBaseUri().toString());
        document.outputSettings().prettyPrint(false);
        String safeHtml = sanitizeSource(document.body(), part.getBaseUri());
        return HTML_CONVERTER.convert(safeHtml).trim();
    }

    private static String sanitizeSource(Element root, URI baseUri) {
        StringBuilder html = new StringBuilder();
        for (Node child : root.childNodes()) appendSafe(child, html, baseUri);
        return html.toString();
    }

    private static void appendSafe(Node node, StringBuilder out, URI baseUri) {
        if (node instanceof TextNode) {
            out.append(((TextNode) node).getWholeText().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"));
            return;
        }
        if (!(node instanceof Element)) return;
        Element element = (Element) node;
        String tag = element.normalName();
        if (tag.equals("script") || tag.equals("style") || tag.equals("iframe") || tag.equals("object") || tag.equals("embed") || tag.equals("svg") || tag.equals("form")) return;
        if (!ALLOWED.contains(tag)) {
            for (Node child : element.childNodes()) appendSafe(child, out, baseUri);
            return;
        }
        out.append('<').append(tag);
        if (tag.equals("a")) appendUrlAttribute(element, "href", baseUri, out);
        if (tag.equals("img")) {
            appendUrlAttribute(element, "src", baseUri, out);
            appendTextAttribute(element, "alt", out);
        }
        if (tag.equals("code") || tag.equals("pre")) {
            String language = languageClass(element);
            if (language.isEmpty() && tag.equals("code") && element.parent() != null && element.parent().normalName().equals("pre")) language = languageClass(element.parent());
            if (!language.isEmpty()) out.append(" class=\"language-").append(language).append("\"");
        }
        if (tag.equals("th") || tag.equals("td")) {
            appendNumericAttribute(element, "colspan", out);
            appendNumericAttribute(element, "rowspan", out);
        }
        out.append('>');
        if (!tag.equals("br") && !tag.equals("hr") && !tag.equals("img")) {
            for (Node child : element.childNodes()) appendSafe(child, out, baseUri);
            out.append("</").append(tag).append('>');
        }
    }

    private static String languageClass(Element element) {
        for (String name : element.classNames()) {
            if (name.matches("(?:language-|lang-)[A-Za-z0-9_+-]{1,32}")) return name.replaceFirst("^(?:language-|lang-)", "");
        }
        return "";
    }

    private static void appendUrlAttribute(Element element, String name, URI baseUri, StringBuilder out) {
        String raw = element.attr(name).trim();
        if (raw.isEmpty()) return;
        try {
            URI uri = new URI(raw);
            if (uri.isAbsolute() && !(uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https") || (name.equals("href") && uri.getScheme().equalsIgnoreCase("mailto")))) return;
            URI resolved = uri.isAbsolute() ? uri : baseUri.resolve(uri);
            out.append(' ').append(name).append("=\"").append(escapeAttribute(resolved.toASCIIString())).append('"');
        } catch (URISyntaxException ignored) { }
    }

    private static void appendTextAttribute(Element element, String name, StringBuilder out) {
        if (element.hasAttr(name)) out.append(' ').append(name).append("=\"").append(escapeAttribute(element.attr(name))).append('"');
    }

    private static void appendNumericAttribute(Element element, String name, StringBuilder out) {
        String value = element.attr(name);
        if (value.matches("[1-9][0-9]{0,2}")) out.append(' ').append(name).append("=\"").append(value).append('"');
    }

    private static String escapeAttribute(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    private static String resolveMarkdownUrls(String markdown, URI baseUri) {
        com.vladsch.flexmark.util.ast.Node document = MARKDOWN_PARSER.parse(markdown);
        List<UrlSpan> spans = new ArrayList<>();
        collectLinkUrls(document, baseUri, spans);
        spans.sort(Comparator.comparingInt((UrlSpan span) -> span.start).reversed());
        StringBuilder result = new StringBuilder(markdown);
        Set<String> replaced = new HashSet<>();
        for (UrlSpan span : spans) {
            String key = span.start + ":" + span.end;
            if (replaced.add(key)) result.replace(span.start, span.end, span.value);
        }
        return result.toString();
    }

    private static void collectLinkUrls(com.vladsch.flexmark.util.ast.Node node, URI baseUri, List<UrlSpan> spans) {
        for (com.vladsch.flexmark.util.ast.Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof InlineLinkNode || child instanceof Reference) {
                BasedSequence url = child instanceof Reference
                        ? ((Reference) child).getUrl()
                        : ((InlineLinkNode) child).getUrl();
                String resolved = resolveUrl(url.toString(), baseUri);
                if (resolved != null && url.getStartOffset() >= 0 && url.getEndOffset() <= Integer.MAX_VALUE) {
                    spans.add(new UrlSpan(url.getStartOffset(), url.getEndOffset(), resolved));
                }
            }
            collectLinkUrls(child, baseUri, spans);
        }
    }

    private static final class UrlSpan {
        private final int start;
        private final int end;
        private final String value;
        private UrlSpan(int start, int end, String value) {
            this.start = start;
            this.end = end;
            this.value = value;
        }
    }

    private static String resolveUrl(String raw, URI baseUri) {
        if (raw.startsWith("#")) return null;
        try {
            URI uri = new URI(raw);
            return uri.isAbsolute() ? null : baseUri.resolve(uri).toASCIIString();
        } catch (URISyntaxException ignored) {
            return null;
        }
    }
}
