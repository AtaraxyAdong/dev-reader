package com.github.yuyuanweb.mianshiyaplugin.reader;

import com.github.yuyuanweb.mianshiyaplugin.reader.data.QuestionRepository;
import com.github.yuyuanweb.mianshiyaplugin.reader.data.ReaderSessionEvents;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.*;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class QuestionTabsTest {
    private final FakeManager manager = new FakeManager();
    private final FakeContent list = new FakeContent();
    private final FakeReader root = new FakeReader();
    private final List<FakeReader> readers = new ArrayList<>();
    private final List<FakeContent> contents = new ArrayList<>();
    private final QuestionTabs tabs;

    public QuestionTabsTest() {
        manager.contents.add(list.content);
        tabs = new QuestionTabs(manager.manager, list.content, root.controller, selection -> {
            FakeReader reader = new FakeReader();
            FakeContent content = new FakeContent();
            content.disposer = reader.controller;
            readers.add(reader);
            contents.add(content);
            return new QuestionTabs.Tab(content.content, reader.controller);
        }, Runnable::run, () -> false);
    }

    @After public void close() { tabs.dispose(); root.controller.dispose(); }

    @Test public void differentQuestionsCreateIndependentNativeContentsAndReopeningSelectsExisting() {
        tabs.open(selection(1));
        Content first = manager.selected;
        tabs.open(selection(2));
        assertNotSame(first, manager.selected);
        tabs.open(selection(1));
        assertSame(first, manager.selected);
        assertEquals(3, manager.contents.size());
        assertEquals(2, readers.size());
        assertEquals(1, readers.get(0).view.loads);
        assertEquals(1, readers.get(1).view.loads);
    }

    @Test public void closingRemovesEntryAndDisposesOnlyThatQuestion() {
        tabs.open(selection(1));
        Content first = manager.selected;
        tabs.open(selection(2));
        manager.manager.removeContent(first, true);
        assertTrue(readers.get(0).executor.isShutdown());
        assertFalse(readers.get(1).executor.isShutdown());
        assertFalse(root.executor.isShutdown());
        tabs.open(selection(1));
        assertNotSame(first, manager.selected);
        assertEquals(3, readers.size());
    }

    @Test public void sessionChangeClosesQuestionsAndSelectsFixedList() {
        tabs.open(selection(1));
        tabs.open(selection(2));
        ReaderSessionEvents.publish(999L);
        assertEquals(Collections.singletonList(list.content), manager.contents);
        assertSame(list.content, manager.selected);
        assertTrue(readers.stream().allMatch(reader -> reader.executor.isShutdown()));
        assertFalse(root.executor.isShutdown());
        tabs.open(selection(1));
        assertEquals(3, readers.size());
    }

    @Test public void disposeUnsubscribesAndDoesNotCreateFurtherContents() {
        tabs.open(selection(1));
        tabs.dispose();
        tabs.dispose();
        ReaderSessionEvents.publish(null);
        tabs.open(selection(2));
        assertEquals(1, readers.size());
        assertTrue(manager.listeners.isEmpty());
        assertEquals(Collections.singletonList(list.content), manager.contents);
        assertTrue(readers.get(0).executor.isShutdown());
    }

    @Test public void fetchedTitleUpdatesTabAndKeepsFullTooltip() {
        String title = "如何配置并管理服务器并且定位网络错误以及解决全部服务配置的问题😀";
        tabs.open(new QuestionSelection(1, title, new NavigationContext(null, Collections.singletonList(1L), 1)));
        assertTrue(contents.get(0).displayName.endsWith("…"));
        assertEquals(title, contents.get(0).description);
        readers.get(0).executor.runAll();
        assertEquals("loaded 1", contents.get(0).displayName);
        assertEquals("loaded 1", contents.get(0).description);
    }

    @Test public void nextAndPreviousReuseCurrentContentAndUpdateListDeduplication() {
        tabs.open(selection(1));
        Content first = manager.selected;
        readers.get(0).controller.nextQuestion();
        assertEquals(2, manager.contents.size());
        assertSame(first, manager.selected);
        assertEquals(2, readers.get(0).view.loads);
        readers.get(0).executor.runAll();
        assertEquals("loaded 2", contents.get(0).displayName);
        tabs.open(selection(2));
        assertSame(first, manager.selected);
        assertEquals(1, readers.size());
        tabs.open(selection(1));
        Content reopenedFirst = manager.selected;
        assertNotSame(first, reopenedFirst);
        assertEquals(2, readers.size());
        manager.manager.setSelectedContent(first);
        readers.get(0).controller.previousQuestion();
        assertSame(first, manager.selected);
        assertEquals(3, manager.contents.size());
        readers.get(0).executor.runAll();
        assertEquals("loaded 1", contents.get(0).displayName);
    }

    @Test public void navigationToQuestionAlreadyOpenElsewhereKeepsBothTabsAndCurrentSelection() {
        tabs.open(selection(1));
        Content first = manager.selected;
        tabs.open(selection(2));
        Content second = manager.selected;
        manager.manager.setSelectedContent(first);
        readers.get(0).controller.nextQuestion();
        assertSame(first, manager.selected);
        assertEquals(3, manager.contents.size());
        assertEquals(2, readers.get(0).view.loads);
        assertEquals(1, readers.get(1).view.loads);
        readers.get(0).executor.runAll();
        readers.get(1).executor.runAll();
        assertEquals("loaded 2", contents.get(0).displayName);
        assertEquals("loaded 2", contents.get(1).displayName);
        manager.manager.removeContent(first, true);
        tabs.open(selection(2));
        assertSame(second, manager.selected);
        assertEquals(2, readers.size());
    }
    private static QuestionSelection selection(long id) {
        return new QuestionSelection(id, "question " + id, new NavigationContext(null, Arrays.asList(1L, 2L), id));
    }

    private static final class FakeReader {
        final ManualExecutor executor = new ManualExecutor();
        final FakeView view = new FakeView();
        final SidebarController controller = new SidebarController(new QuestionRepository() {
            @Override public QuestionLoadResult loadQuestion(long id, Long bankId) {
                URI uri = URI.create("https://www.mianshiya.com/question/" + id);
                ContentPart body = new ContentPart(PartStatus.AVAILABLE, SourceFormat.MARKDOWN, "body", uri);
                return QuestionLoadResult.ready(new QuestionDocument(id, "loaded " + id, body, body, uri));
            }
            @Override public NavigationContext loadNavigation(NavigationContext current) { return current; }
        }, view, executor, Runnable::run, doc -> new RenderedQuestion(doc.getId(), doc.getTitle(), doc.getSourceUri()));
    }

    private static final class FakeView implements ReaderView {
        int loads;
        @Override public void showBanks() { }
        @Override public void showQuestions(Long bankId) { }
        @Override public void restoreQuestionList() { }
        @Override public void showLoading(QuestionSelection selection) { loads++; }
        @Override public void showDocument(RenderedQuestion document, ReadingPosition position) { }
        @Override public void showFailure(QuestionLoadResult result) { }
        @Override public void updateNavigation(boolean previous, boolean next) { }
        @Override public void clearSessionContent() { }
    }

    private static final class FakeContent {
        String displayName;
        String description;
        Disposable disposer;
        final Content content = (Content) Proxy.newProxyInstance(Content.class.getClassLoader(), new Class<?>[]{Content.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setDisplayName": displayName = (String) args[0]; return null;
                        case "setDescription": description = (String) args[0]; return null;
                        case "getDisposer": return disposer;
                        case "equals": return proxy == args[0];
                        case "hashCode": return System.identityHashCode(proxy);
                        case "toString": return "FakeContent(" + displayName + ")";
                        default: return null;
                    }
                });
    }

    private static final class FakeManager {
        final List<Content> contents = new ArrayList<>();
        final List<ContentManagerListener> listeners = new ArrayList<>();
        Content selected;
        final ContentManager manager = (ContentManager) Proxy.newProxyInstance(ContentManager.class.getClassLoader(),
                new Class<?>[]{ContentManager.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isDisposed": return false;
                        case "addContent": contents.add((Content) args[0]); return null;
                        case "getIndexOfContent": return contents.indexOf(args[0]);
                        case "setSelectedContent": selected = (Content) args[0]; return null;
                        case "addContentManagerListener": listeners.add((ContentManagerListener) args[0]); return null;
                        case "removeContentManagerListener": listeners.remove(args[0]); return null;
                        case "removeContent": {
                            Content removed = (Content) args[0];
                            int index = contents.indexOf(removed);
                            if (index < 0) return false;
                            contents.remove(index);
                            ContentManagerEvent event = new ContentManagerEvent(proxy, removed, index);
                            for (ContentManagerListener listener : new ArrayList<>(listeners)) listener.contentRemoved(event);
                            if ((boolean) args[1]) Disposer.dispose(removed.getDisposer());
                            return true;
                        }
                        case "equals": return proxy == args[0];
                        case "hashCode": return System.identityHashCode(proxy);
                        default: return null;
                    }
                });
    }

    private static final class ManualExecutor extends AbstractExecutorService {
        final Queue<Runnable> tasks = new ArrayDeque<>();
        boolean shutdown;
        void runAll() { while (!tasks.isEmpty()) tasks.remove().run(); }
        @Override public void execute(Runnable task) { tasks.add(task); }
        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() { shutdown = true; tasks.clear(); return Collections.emptyList(); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return shutdown; }
    }
}
