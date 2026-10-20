package com.github.yuyuanweb.mianshiyaplugin.reader;

import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.*;
import com.github.yuyuanweb.mianshiyaplugin.utils.ThemeUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefJSQuery;
import com.intellij.util.messages.MessageBusConnection;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.handler.CefCookieAccessFilter;
import org.cef.misc.BoolRef;
import org.cef.network.CefCookie;
import org.cef.network.CefResponse;
import org.cef.network.CefRequest;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;

/** One lazy browser for a project's reader. Resizing never loads another document. */
public final class QuestionReaderPanel extends JPanel implements Disposable {
    private static final String LOCAL_URL_PREFIX = "file:///mianshiya-reader/";
    private final Project project;
    private final BiConsumer<Long, ReadingPosition> progress;
    private final Gson gson = new Gson();
    private final Map<Long, Boolean> answerExpanded = new HashMap<>();
    private final CardLayout cards = new CardLayout();
    private final JTextArea fallback = new JTextArea();
    private JBCefBrowser browser;
    private JBCefJSQuery query;
    private volatile boolean disposed;
    private volatile String currentDocumentUrl = "about:blank";
    private String token = "";
    private long questionId;
    private ReadingPosition position = ReadingPosition.TOP;
    private URI sourceUri;

    public QuestionReaderPanel(Project project, BiConsumer<Long, ReadingPosition> progress) {
        super();
        this.project = project;
        this.progress = progress;
        setLayout(cards);
        setMinimumSize(new Dimension(0, 0));
        fallback.setEditable(false);
        fallback.setLineWrap(true);
        fallback.setWrapStyleWord(true);
        fallback.setOpaque(false);
        fallback.setBorder(com.intellij.util.ui.JBUI.Borders.empty(12));
        add(fallback, "status");
        MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(this);
        connection.subscribe(LafManagerListener.TOPIC, manager -> refreshTheme());
        connection.subscribe(EditorColorsManager.TOPIC, scheme -> refreshTheme());
    }

    public void showStatus(String text) {
        token = UUID.randomUUID().toString();
        fallback.setText(text);
        cards.show(this, "status");
    }

    public void showDocument(RenderedQuestion document, ReadingPosition savedPosition) {
        questionId = document.getId();
        sourceUri = document.getSourceUri();
        position = savedPosition;
        token = UUID.randomUUID().toString();
        if (!ensureBrowser()) return;
        String nonce = UUID.randomUUID().toString();
        String script = resource("reader.js") + "\nwindow.readerBridge = function(message) {" + query.inject("message")
                + "}; window.readerStart(" + gson.toJson(documentOptions()) + ");";
        String template = resource("reader.html").replace("{{NONCE}}", nonce)
                .replace("{{TOKEN}}", token)
                .replace("{{CSS}}", resource("reader.css"))
                .replace("{{JS}}", script)
                .replace("{{CONTENT}}", document.getHtml());
        // Explicit file URLs are preserved by both supported SDKs. Each document needs
        // a distinct URL because newer SDKs deduplicate an in-flight load of the same URL.
        currentDocumentUrl = LOCAL_URL_PREFIX + token + "/index.html";
        browser.loadHTML(template, currentDocumentUrl);
    }

    private boolean ensureBrowser() {
        if (browser != null) return true;
        if (!JBCefApp.isSupported()) {
            showStatus(ReaderMessages.text("browser.unsupported"));
            return false;
        }
        try {
            browser = new JBCefBrowser();
            Disposer.register(this, browser);
            query = JBCefJSQuery.create(browser);
            Disposer.register(this, query);
            query.addHandler(message -> {
                dispatchMessage(message);
                return new JBCefJSQuery.Response("");
            });
            browser.getJBCefClient().addRequestHandler(new CefRequestHandlerAdapter() {
                @Override public CefResourceRequestHandler getResourceRequestHandler(CefBrowser cefBrowser, CefFrame frame,
                        CefRequest request, boolean isNavigation, boolean isDownload, String requestInitiator, BoolRef disableDefaultHandling) {
                    if (isCurrentDocument(request.getURL())) return null;
                    return new CefResourceRequestHandlerAdapter() {
                        @Override public CefCookieAccessFilter getCookieAccessFilter(CefBrowser b, CefFrame f, CefRequest r) {
                            return new CefCookieAccessFilter() {
                                @Override public boolean canSendCookie(CefBrowser cb, CefFrame cf, CefRequest cr, CefCookie cookie) { return false; }
                                @Override public boolean canSaveCookie(CefBrowser cb, CefFrame cf, CefRequest cr, CefResponse response, CefCookie cookie) { return false; }
                            };
                        }
                    };
                }
                @Override public boolean onBeforeBrowse(CefBrowser cefBrowser, CefFrame frame, CefRequest request,
                                                        boolean userGesture, boolean isRedirect) {
                    if (!frame.isMain()) return true;
                    return !isCurrentDocument(request.getURL())
                            && !request.getURL().equals("about:blank");
                }
            }, browser.getCefBrowser());
            add(browser.getComponent(), "browser");
            return true;
        } catch (RuntimeException failure) {
            if (browser != null) Disposer.dispose(browser);
            browser = null;
            showStatus(ReaderMessages.text("browser.failed"));
            return false;
        }
    }

    private Map<String, Object> documentOptions() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("token", token);
        options.put("position", position);
        options.put("theme", ThemeUtil.readerVariables());
        options.put("answerExpanded", answerExpanded.getOrDefault(questionId, true));
        options.put("imageFailure", ReaderMessages.text("image.failed"));
        options.put("sourceUri", sourceUri == null ? "" : sourceUri.toString());
        return options;
    }

    private boolean isCurrentDocument(String url) {
        String expected = currentDocumentUrl;
        return expected.equals(url) || url.startsWith(expected + "#");
    }

    private void dispatchMessage(String message) {
        if (message == null || message.length() > 16384) return;
        final JsonObject data;
        try { data = gson.fromJson(message, JsonObject.class); } catch (RuntimeException malformed) { return; }
        onUi(() -> {
            try {
                if (data == null || !data.has("token") || !token.equals(data.get("token").getAsString())) return;
                switch (data.get("kind").getAsString()) {
                    case "ready": cards.show(this, "browser"); break;
                    case "position":
                        position = new ReadingPosition(data.get("blockId").getAsString(), data.get("offsetRatio").getAsDouble());
                        progress.accept(questionId, position);
                        break;
                    case "answer": answerExpanded.put(questionId, data.get("expanded").getAsBoolean()); break;
                    case "link": openExternal(data.get("url").getAsString()); break;
                    default: break;
                }
            } catch (RuntimeException malformed) { /* Ignore invalid browser messages. */ }
        });
    }

    private static void openExternal(String url) {
        try {
            URI uri = URI.create(url);
            if ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) BrowserUtil.browse(uri.toString());
        } catch (IllegalArgumentException ignored) { }
    }

    private void refreshTheme() {
        onUi(() -> execute("if (window.readerApplyTheme) window.readerApplyTheme(" + gson.toJson(ThemeUtil.readerVariables()) + ");"));
    }

    private void execute(String script) {
        if (!disposed && browser != null) browser.getCefBrowser().executeJavaScript(script, currentDocumentUrl, 0);
    }

    private void onUi(Runnable action) {
        ToolWindowManager.getInstance(project).invokeLater(() -> { if (!disposed && !project.isDisposed()) action.run(); });
    }

    public void toggleAnswer() { execute("if (window.readerToggleAnswer) window.readerToggleAnswer();"); }

    public void clearSession() {
        token = UUID.randomUUID().toString();
        answerExpanded.clear();
        sourceUri = null;
        questionId = 0;
        position = ReadingPosition.TOP;
        if (browser != null) {
            currentDocumentUrl = LOCAL_URL_PREFIX + token + "/index.html";
            browser.loadHTML("", currentDocumentUrl);
        }
        showStatus("");
    }

    private static String resource(String name) {
        try (InputStream input = QuestionReaderPanel.class.getResourceAsStream("/reader/" + name)) {
            if (input == null) throw new IllegalStateException("Missing reader resource: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) { throw new IllegalStateException("Cannot load reader resource", error); }
    }

    @Override public void dispose() { disposed = true; token = ""; answerExpanded.clear(); }
}
