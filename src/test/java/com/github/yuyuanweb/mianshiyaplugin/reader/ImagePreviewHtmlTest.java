package com.github.yuyuanweb.mianshiyaplugin.reader;
import org.junit.Test;
import org.jsoup.Jsoup;
import java.net.URI;
import java.util.Collections;
import static org.junit.Assert.*;
public class ImagePreviewHtmlTest {
    @Test public void hostileAltCannotEscapeTheTrustedScriptOrCreateRemoteMarkup() {
        String alt="</script><img src=https://evil.invalid/x onerror=alert(1)><script>";
        var doc=Jsoup.parse(ImagePreviewHtml.render(URI.create("https://example.com/image.png"),alt,"",Collections.emptyMap()));
        assertEquals(1,doc.select("script").size());
        assertTrue(doc.select("img[src], [onerror], script[src]").isEmpty());
        assertEquals(1,doc.select("#image-preview").size());
    }
    @Test(expected=IllegalArgumentException.class) public void localFileCannotBecomeAPreviewResource() {
        ImagePreviewHtml.render(URI.create("file:///private.png"),"","",Collections.emptyMap());
    }
}
