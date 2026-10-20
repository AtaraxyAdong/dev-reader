package com.github.yuyuanweb.mianshiyaplugin.reader;

import com.github.yuyuanweb.mianshiyaplugin.utils.ThemeUtil;
import com.google.gson.Gson;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.*;
import org.cef.misc.BoolRef;
import org.cef.network.*;
import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Modeless IDE-owned window, centered on the IDE's monitor rather than a ToolWindow. */
final class ImagePreviewDialog extends DialogWrapper implements ImagePreviewController.Preview {
    private final Window ideWindow;
    private final URI image;
    private final String alt;
    private final Runnable closed;
    private final String token = UUID.randomUUID().toString();
    private final String localUrl = "file:///mianshiya-image-preview/" + token + "/index.html";
    private boolean released;

    ImagePreviewDialog(Project project, Window ideWindow, URI image, String alt, Runnable closed) {
        super(project, false);
        this.ideWindow = ideWindow; this.image = image; this.alt = alt; this.closed = closed;
        setTitle("图片预览"); setModal(false); setResizable(true);
        try { init(); getCancelAction().putValue(Action.NAME, "关闭"); }
        catch (RuntimeException failure) { dispose(); throw failure; }
    }
    @Override protected JComponent createCenterPanel() {
        if (!JBCefApp.isSupported()) return new JLabel("当前 IDE 未提供图片预览所需的 JCEF 支持。");
        JBCefBrowser browser = new JBCefBrowser();
        Disposer.register(getDisposable(), browser);
        JBCefJSQuery query = JBCefJSQuery.create(browser);
        Disposer.register(getDisposable(), query);
        query.addHandler(message -> {
            if (token.equals(message)) ApplicationManager.getApplication().invokeLater(() -> {
                if (!isDisposed()) doCancelAction();
            });
            return new JBCefJSQuery.Response("");
        });
        browser.getJBCefClient().addRequestHandler(new CefRequestHandlerAdapter() {
            @Override public CefResourceRequestHandler getResourceRequestHandler(CefBrowser b, CefFrame frame,
                    CefRequest request, boolean navigation, boolean download, String initiator, BoolRef handling) {
                if (isLocal(request.getURL())) return null;
                return new CefResourceRequestHandlerAdapter() {
                    @Override public CefCookieAccessFilter getCookieAccessFilter(CefBrowser cb, CefFrame cf, CefRequest cr) {
                        return new CefCookieAccessFilter() {
                            @Override public boolean canSendCookie(CefBrowser c, CefFrame f, CefRequest r, CefCookie cookie) { return false; }
                            @Override public boolean canSaveCookie(CefBrowser c, CefFrame f, CefRequest r, CefResponse response, CefCookie cookie) { return false; }
                        };
                    }
                };
            }
            @Override public boolean onBeforeBrowse(CefBrowser b, CefFrame frame, CefRequest request, boolean gesture, boolean redirect) {
                return !frame.isMain() || (!isLocal(request.getURL()) && !"about:blank".equals(request.getURL()));
            }
        }, browser.getCefBrowser());
        JComponent component = browser.getComponent();
        component.setPreferredSize(new Dimension(900, 650));
        browser.loadHTML(ImagePreviewHtml.render(image, alt, query.inject(new Gson().toJson(token)), ThemeUtil.readerVariables()), localUrl);
        return component;
    }
    private boolean isLocal(String url) { return localUrl.equals(url) || url.startsWith(localUrl + "#"); }
    @Override protected Action[] createActions() { return new Action[]{getCancelAction()}; }
    @Override public void show() {
        List<Rectangle> screens = new ArrayList<>();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            GraphicsConfiguration config = device.getDefaultConfiguration();
            screens.add(ImagePreviewPlacement.usable(config.getBounds(), Toolkit.getDefaultToolkit().getScreenInsets(config)));
        }
        Rectangle bounds = ImagePreviewPlacement.centered(ideWindow == null ? null : ideWindow.getBounds(), screens);
        setSize(bounds.width, bounds.height);
        setInitialLocationCallback(bounds::getLocation);
        super.show();
    }
    @Override public void close() { if (!isDisposed()) doCancelAction(); }
    @Override protected void dispose() {
        if (released) return;
        released = true;
        try { super.dispose(); } finally { closed.run(); }
    }
}
