package com.github.yuyuanweb.mianshiyaplugin.reader;

import com.github.yuyuanweb.mianshiyaplugin.reader.data.QuestionRepository;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.*;
import org.junit.After;
import org.junit.Test;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;

public class SidebarControllerTest {
    private final ManualExecutor background = new ManualExecutor();
    private final Queue<Runnable> ui = new ArrayDeque<>();
    private final FakeView view = new FakeView();
    private final FakeRepository repository = new FakeRepository();
    private final SidebarController controller = new SidebarController(repository, view, background, ui::add,
            doc -> new RenderedQuestion(doc.getId(), doc.getTitle(), doc.getSourceUri()));

    @After public void close() { controller.dispose(); }

    @Test public void ignoresLateResultFromAAfterOpeningB() {
        controller.showQuestion(selection(1, 1, 2));
        background.runAll(); // A completes but its UI delivery is delayed.
        controller.showQuestion(selection(2, 1, 2));
        background.runAll();
        drainUi();
        assertEquals(Collections.singletonList(2L), view.shown);
    }

    @Test public void doesNotReopenDetailAfterBack() {
        controller.showQuestion(selection(1, 1, 2));
        background.runAll();
        controller.backToList();
        drainUi();
        assertTrue(view.shown.isEmpty());
        assertEquals(1, view.restores);
    }

    @Test public void clearsContentOnSessionChange() {
        controller.showQuestion(selection(1, 1, 2));
        background.runAll();
        controller.onSessionChanged();
        drainUi();
        assertTrue(view.shown.isEmpty());
        assertEquals(1, view.clears);
    }

    @Test public void ignoresCallbackAfterDispose() {
        controller.showQuestion(selection(1, 1));
        background.runAll();
        controller.dispose();
        drainUi();
        assertTrue(view.shown.isEmpty());
        assertTrue(background.isShutdown());
    }

    @Test public void disablesNavigationAtSnapshotEdges() {
        controller.showQuestion(selection(1, 1, 2));
        assertFalse(view.previous);
        assertTrue(view.next);
        controller.previousQuestion();
        assertEquals(1, background.queue.size());
        controller.nextQuestion();
        assertTrue(view.previous);
        assertFalse(view.next);
    }

    @Test public void keepsDocumentWhenNavigationFails() {
        repository.failNavigation = true;
        controller.showQuestion(new QuestionSelection(1, "one", new NavigationContext(5L, Arrays.asList(1L, 2L), 1)));
        background.runAll();
        drainUi();
        assertEquals(Collections.singletonList(1L), view.shown);
        assertFalse(view.next);
        assertNull(view.failure);
    }

    @Test public void bankNavigationWaitsForAuthoritativeOrder() {
        controller.showQuestion(new QuestionSelection(1, "one", new NavigationContext(5L, Arrays.asList(1L, 2L), 1)));
        assertFalse(view.next);
        background.runAll(); drainUi();
        assertTrue(view.next);
    }

    @Test public void restoresReadingPositionForVisitedQuestion() {
        controller.showQuestion(selection(1, 1, 2));
        background.runAll(); drainUi();
        controller.savePosition(1, new ReadingPosition("body-3", .4));
        controller.nextQuestion();
        background.runAll(); drainUi();
        controller.previousQuestion();
        background.runAll(); drainUi();
        assertEquals("body-3", view.position.getBlockId());
        assertEquals(.4, view.position.getOffsetRatio(), .001);
    }

    @Test public void projectsHaveIndependentSelectionAndProgress() {
        FakeView another = new FakeView();
        SidebarController other = new SidebarController(repository, another, new ManualExecutor(), ui::add,
                doc -> new RenderedQuestion(doc.getId(), "", doc.getSourceUri()));
        try {
            controller.showQuestion(selection(1, 1));
            background.runAll(); drainUi();
            assertTrue(another.shown.isEmpty());
            assertEquals(Collections.singletonList(1L), view.shown);
        } finally { other.dispose(); }
    }

    @Test public void loadingFailureIsNotRenderedAsEmptyAnswer() {
        repository.failLoad = true;
        controller.showQuestion(selection(1, 1));
        background.runAll(); drainUi();
        assertTrue(view.shown.isEmpty());
        assertEquals(LoadStatus.NEEDS_LOGIN, view.failure.getStatus());
    }

    @Test public void listOpeningAndInTabNavigationUseSeparateRoutes() {
        List<Long> opened = new ArrayList<>();
        controller.configureTabs(s -> opened.add(s.getId()), () -> {}, null, title -> {});
        controller.showQuestion(selection(1, 1, 2));
        assertEquals(Collections.singletonList(1L), opened);
        assertTrue(background.queue.isEmpty());
        controller.setInTabNavigator(s -> opened.add(s.getId()));
        controller.openInCurrentTab(selection(1, 1, 2));
        background.runAll(); drainUi();
        controller.nextQuestion();
        assertEquals(Arrays.asList(1L, 2L), opened);
        assertEquals(Collections.singletonList(1L), view.shown);
        assertTrue(background.queue.isEmpty());
    }
    @Test public void retryReloadsCurrentTabInsteadOfSelectingItAgain() {
        List<Long> opened = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        controller.configureTabs(s -> opened.add(s.getId()), () -> {}, null, titles::add);
        repository.failLoad = true;
        controller.openInCurrentTab(selection(1, 1));
        background.runAll(); drainUi();
        repository.failLoad = false;
        controller.retry();
        background.runAll(); drainUi();
        assertTrue(opened.isEmpty());
        assertEquals(Collections.singletonList(1L), view.shown);
        assertEquals(Collections.singletonList("question 1"), titles);
    }
    @Test public void returningToListKeepsDetailLoadAndReadingPosition() {
        FakeView listView = new FakeView();
        int[] selected = {0};
        SidebarController list = new SidebarController(repository, listView, new ManualExecutor(), ui::add,
                doc -> new RenderedQuestion(doc.getId(), "", doc.getSourceUri()));
        try {
            list.configureTabs(s -> {}, () -> selected[0]++, null, title -> {});
            controller.configureTabs(s -> {}, () -> {}, list, title -> {});
            controller.openInCurrentTab(selection(1, 1));
            controller.backToList();
            background.runAll(); drainUi();
            assertEquals(1, listView.restores);
            assertEquals(1, selected[0]);
            assertEquals(Collections.singletonList(1L), view.shown);
            controller.savePosition(1, new ReadingPosition("answer-4", .6));
            controller.retry();
            background.runAll(); drainUi();
            assertEquals("answer-4", view.position.getBlockId());
        } finally { list.dispose(); }
    }
    @Test public void independentTabsDeliverLateResultsToTheirOwnViews() {
        FakeView otherView = new FakeView();
        ManualExecutor otherTasks = new ManualExecutor();
        SidebarController other = new SidebarController(repository, otherView, otherTasks, ui::add,
                doc -> new RenderedQuestion(doc.getId(), "", doc.getSourceUri()));
        try {
            controller.openInCurrentTab(selection(1, 1, 2));
            other.openInCurrentTab(selection(2, 1, 2));
            otherTasks.runAll(); background.runAll(); drainUi();
            assertEquals(Collections.singletonList(1L), view.shown);
            assertEquals(Collections.singletonList(2L), otherView.shown);
            controller.dispose();
            other.retry(); otherTasks.runAll(); drainUi();
            assertEquals(Arrays.asList(2L, 2L), otherView.shown);
        } finally { other.dispose(); }
    }
    @Test public void closingQuestionControllerDoesNotStopProjectWorkerPool() {
        SidebarController child = new SidebarController(null, controller);
        child.dispose();
        assertFalse(background.isShutdown());
        controller.showQuestion(selection(1, 1));
        background.runAll(); drainUi();
        assertEquals(Collections.singletonList(1L), view.shown);
    }
    private void drainUi() { while (!ui.isEmpty()) ui.remove().run(); }
    private static QuestionSelection selection(long id, long... ids) {
        List<Long> snapshot = new ArrayList<>();
        for (long value : ids) snapshot.add(value);
        return new QuestionSelection(id, "question " + id, new NavigationContext(null, snapshot, id));
    }

    private static final class FakeRepository implements QuestionRepository {
        boolean failNavigation;
        boolean failLoad;
        @Override public QuestionLoadResult loadQuestion(long id, Long bankId) {
            if (failLoad) return QuestionLoadResult.failure(LoadStatus.NEEDS_LOGIN, "login");
            URI uri = URI.create("https://www.mianshiya.com/question/" + id);
            ContentPart part = new ContentPart(PartStatus.AVAILABLE, SourceFormat.MARKDOWN, "body", uri);
            return QuestionLoadResult.ready(new QuestionDocument(id, "question " + id, part, part, uri));
        }
        @Override public NavigationContext loadNavigation(NavigationContext current) {
            if (failNavigation) throw new IllegalStateException("offline");
            return new NavigationContext(current.getBankId(), current.getOrderedQuestionIds(), current.getSelectedId(), true);
        }
    }

    private static final class FakeView implements ReaderView {
        List<Long> shown = new ArrayList<>();
        ReadingPosition position;
        QuestionLoadResult failure;
        boolean previous;
        boolean next;
        int restores;
        int clears;
        @Override public void showBanks() { }
        @Override public void showQuestions(Long bankId) { }
        @Override public void restoreQuestionList() { restores++; }
        @Override public void showLoading(QuestionSelection selection) { }
        @Override public void showDocument(RenderedQuestion doc, ReadingPosition pos) { shown.add(doc.getId()); position = pos; }
        @Override public void showFailure(QuestionLoadResult result) { failure = result; }
        @Override public void updateNavigation(boolean previous, boolean next) { this.previous = previous; this.next = next; }
        @Override public void clearSessionContent() { clears++; }
    }

    private static final class ManualExecutor extends AbstractExecutorService {
        final Queue<Runnable> queue = new ArrayDeque<>();
        boolean shutdown;
        void runAll() { while (!queue.isEmpty()) queue.remove().run(); }
        @Override public void execute(Runnable command) { queue.add(command); }
        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() { shutdown = true; queue.clear(); return Collections.emptyList(); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return shutdown; }
    }
}
