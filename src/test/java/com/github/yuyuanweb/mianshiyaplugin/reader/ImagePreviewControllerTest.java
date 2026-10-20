package com.github.yuyuanweb.mianshiyaplugin.reader;
import org.junit.Test;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class ImagePreviewControllerTest {
    private static class WindowHandle implements ImagePreviewController.Preview {
        boolean open; int closed;
        public void show() { open = true; }
        public void close() { open = false; closed++; }
    }
    @Test public void repeatedOpenReplacesAndReleasesThePreviousWindow() {
        List<WindowHandle> handles = new ArrayList<>();
        ImagePreviewController owner = new ImagePreviewController((uri,alt) -> { WindowHandle h=new WindowHandle(); handles.add(h); return h; });
        for(int i=0;i<10;i++) owner.open("https://example.com/image.png", "diagram");
        assertEquals(10, handles.size());
        for(int i=0;i<9;i++) { assertFalse(handles.get(i).open); assertEquals(1,handles.get(i).closed); }
        assertTrue(handles.get(9).open); owner.close(); owner.close();
        assertFalse(handles.get(9).open); assertEquals(1,handles.get(9).closed);
    }
    @Test public void documentChangeAndProjectDisposalReleaseEvenAFailedImageWindow() {
        WindowHandle handle = new WindowHandle();
        ImagePreviewController owner = new ImagePreviewController((uri,alt)->handle);
        owner.open("https://example.com/failed.png", ""); owner.close(); assertFalse(handle.open);
        owner.open("https://example.com/second.png", ""); owner.dispose(); assertFalse(handle.open);
        int closes=handle.closed; owner.open("https://example.com/third.png", ""); owner.dispose(); assertEquals(closes,handle.closed);
    }
    @Test public void failedWindowShowClosesItsResourcesAndCanBeRetried() {
        WindowHandle handle = new WindowHandle() { @Override public void show() { throw new IllegalStateException("test"); } };
        ImagePreviewController owner = new ImagePreviewController((uri,alt)->handle);
        try { owner.open("https://example.com/image.png", ""); fail(); } catch (IllegalStateException expected) { }
        assertEquals(1,handle.closed); owner.close(); assertEquals(1,handle.closed);
    }
    @Test public void rejectsUnsafeOrCredentialUrlsBeforeCreatingAWindow() {
        List<URI> opened=new ArrayList<>();
        ImagePreviewController owner=new ImagePreviewController((uri,alt)->{opened.add(uri);return new WindowHandle();});
        for(String value:new String[]{"javascript:alert(1)","file:///private.png","data:image/png,xx","https://user:password@example.com/a","http:///missing-host", "bad url"}) owner.open(value, "");
        assertTrue(opened.isEmpty()); owner.open("https://example.com/diagram.png?q=1", "diagram");
        assertEquals(URI.create("https://example.com/diagram.png?q=1"),opened.get(0));
    }
}
