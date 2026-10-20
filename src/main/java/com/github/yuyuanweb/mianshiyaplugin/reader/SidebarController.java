package com.github.yuyuanweb.mianshiyaplugin.reader;

import com.github.yuyuanweb.mianshiyaplugin.constant.KeyConstant;
import com.github.yuyuanweb.mianshiyaplugin.reader.data.ApiQuestionRepository;
import com.github.yuyuanweb.mianshiyaplugin.reader.data.QuestionRepository;
import com.github.yuyuanweb.mianshiyaplugin.reader.data.ReaderSessionEvents;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.concurrency.AppExecutorUtil;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

/** Project-scoped navigation and loading. All view callbacks are performed on the UI dispatcher. */
public final class SidebarController implements Disposable {
    private final QuestionRepository repository;
    private final ExecutorService executor;
    private final boolean ownsExecutor;
    private final Consumer<Runnable> ui;
    private final Function<QuestionDocument, RenderedQuestion> render;
    private final AtomicLong requestGeneration = new AtomicLong();
    private final AtomicLong sessionGeneration = new AtomicLong();
    private final Map<Long, ReadingPosition> positions = new ConcurrentHashMap<>();
    private final Map<Long, String> titles = new ConcurrentHashMap<>();
    private final Project project;
    private ReaderView view;
    private volatile QuestionSelection selected;
    private volatile boolean disposed;
    private Future<?> pendingLoad;
    private Future<?> pendingNavigation;
    private AutoCloseable sessionSubscription;
    private boolean detailVisible;
    private Consumer<QuestionSelection> questionOpener;
    private Consumer<QuestionSelection> inTabNavigator = this::openInCurrentTab;
    private Runnable selectView = () -> {};
    private SidebarController listController;
    private Consumer<String> titleChanged = title -> {};

    public SidebarController(Project project) { this(project, null); }

    /** Each question owns its state and requests, while the project owns the shared worker pool. */
    SidebarController(Project project, SidebarController owner) {
        this.project = project;
        this.repository = owner == null ? new ApiQuestionRepository() : owner.repository;
        this.ownsExecutor = owner == null;
        this.executor = owner == null ? AppExecutorUtil.createBoundedApplicationPoolExecutor("Mianshiya reader", 2) : owner.executor;
        this.ui = task -> ToolWindowManager.getInstance(project).invokeLater(() -> {
            if (!project.isDisposed() && !disposed) task.run();
        });
        this.render = QuestionHtmlBuilder::render;
        this.sessionSubscription = ReaderSessionEvents.addListener((generation, userId) -> onSessionChanged());
    }

    SidebarController(QuestionRepository repository, ReaderView view, ExecutorService executor,
                             Consumer<Runnable> ui, Function<QuestionDocument, RenderedQuestion> render) {
        this.project = null;
        this.ownsExecutor = true;
        this.repository = Objects.requireNonNull(repository);
        this.view = Objects.requireNonNull(view);
        this.executor = Objects.requireNonNull(executor);
        this.ui = Objects.requireNonNull(ui);
        this.render = Objects.requireNonNull(render);
    }

    public static SidebarController getInstance(Project project) {
        return Objects.requireNonNull(project, "Project is required").getService(SidebarController.class);
    }

    public void configureTabs(Consumer<QuestionSelection> opener, Runnable selectView,
                              SidebarController listController, Consumer<String> titleChanged) {
        this.questionOpener = Objects.requireNonNull(opener);
        this.selectView = Objects.requireNonNull(selectView);
        this.listController = listController;
        this.titleChanged = Objects.requireNonNull(titleChanged);
    }

    /** Used only for a newly created tab or an explicit retry; regular navigation goes through the tab host. */
    public void openInCurrentTab(QuestionSelection selection) { withView(() -> openQuestion(selection)); }

    public void setInTabNavigator(Consumer<QuestionSelection> navigate) {
        this.inTabNavigator = Objects.requireNonNull(navigate);
    }

    public void attachView(ReaderView view) { this.view = Objects.requireNonNull(view); }

    private void withView(Runnable action) {
        if (disposed) return;
        if (view != null) { action.run(); return; }
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(KeyConstant.PLUGIN_NAME);
        if (window != null) window.show(() -> { if (!disposed && view != null) action.run(); });
    }

    public void showBanks() {
        if (listController != null) { listController.showBanks(); return; }
        withView(() -> { selectView.run(); invalidate(); detailVisible = false; view.showBanks(); });
    }

    public void showQuestions(Long bankId) {
        if (listController != null) { listController.showQuestions(bankId); return; }
        withView(() -> { selectView.run(); invalidate(); detailVisible = false; view.showQuestions(bankId); });
    }

    public void showQuestion(QuestionSelection selection) {
        withView(() -> {
            if (questionOpener != null) questionOpener.accept(selection);
            else openQuestion(selection);
        });
    }

    private void openQuestion(QuestionSelection selection) {
        invalidate();
        selected = selection;
        detailVisible = true;
        titles.put(selection.getId(), selection.getTitle());
        view.showLoading(selection);
        updateNavigation();
        final long request = requestGeneration.get();
        final long session = sessionGeneration.get();
        pendingLoad = executor.submit(() -> {
            QuestionLoadResult result;
            RenderedQuestion rendered = null;
            try {
                result = repository.loadQuestion(selection.getId(), selection.getNavigation().getBankId());
                if (result.getStatus() == LoadStatus.READY) rendered = render.apply(result.getDocument());
            } catch (RuntimeException error) {
                result = QuestionLoadResult.failure(LoadStatus.ERROR, ReaderMessages.text("load.failed"));
            }
            final QuestionLoadResult response = result;
            final RenderedQuestion document = rendered;
            ui.accept(() -> {
                if (!isCurrent(request, session, selection.getId())) return;
                if (response.getStatus() == LoadStatus.READY) {
                    titles.put(selection.getId(), response.getDocument().getTitle());
                    view.showDocument(document, positions.getOrDefault(selection.getId(), ReadingPosition.TOP));
                    titleChanged.accept(response.getDocument().getTitle());
                } else if (response.getStatus() != LoadStatus.CANCELLED) {
                    view.showFailure(response);
                }
            });
        });
        if (selection.getNavigation().getBankId() != null && !selection.getNavigation().isBankOrderResolved()) {
            pendingNavigation = executor.submit(() -> {
                try {
                    NavigationContext navigation = repository.loadNavigation(selection.getNavigation());
                    if (navigation == null || !navigation.getOrderedQuestionIds().contains(selection.getId())) return;
                    ui.accept(() -> {
                        if (!isCurrent(request, session, selection.getId())) return;
                        selected = new QuestionSelection(selection.getId(), selection.getTitle(), navigation);
                        updateNavigation();
                    });
                } catch (RuntimeException ignored) {
                    // A valid current-page snapshot stays usable when bank navigation is unavailable.
                }
            });
        }
    }

    private boolean isCurrent(long request, long session, long id) {
        return !disposed && detailVisible && requestGeneration.get() == request && sessionGeneration.get() == session
                && selected != null && selected.getId() == id;
    }

    public void backToList() {
        if (listController != null) { listController.backToList(); return; }
        withView(() -> { selectView.run(); invalidate(); detailVisible = false; view.restoreQuestionList(); });
    }

    public void previousQuestion() { move(-1); }
    public void nextQuestion() { move(1); }
    private void move(int offset) {
        if (disposed || !detailVisible || selected == null) return;
        NavigationContext navigation = selected.getNavigation();
        if (navigation.getBankId() != null && !navigation.isBankOrderResolved()) return;
        List<Long> ids = navigation.getOrderedQuestionIds();
        int current = ids.indexOf(selected.getId());
        int next = current + offset;
        if (current < 0 || next < 0 || next >= ids.size()) return;
        long id = ids.get(next);
        String title = titles.getOrDefault(id, ReaderMessages.text("question.number", id));
        inTabNavigator.accept(new QuestionSelection(id, title, new NavigationContext(navigation.getBankId(), ids, id, navigation.isBankOrderResolved())));
    }

    public void retry() { if (selected != null && detailVisible) openInCurrentTab(selected); }

    private void updateNavigation() {
        if (selected == null || (selected.getNavigation().getBankId() != null && !selected.getNavigation().isBankOrderResolved())) {
            view.updateNavigation(false, false); return;
        }
        List<Long> ids = selected.getNavigation().getOrderedQuestionIds();
        int index = ids.indexOf(selected.getId());
        view.updateNavigation(index > 0, index >= 0 && index < ids.size() - 1);
    }

    public void savePosition(long id, ReadingPosition position) {
        if (!disposed && detailVisible && selected != null && selected.getId() == id) positions.put(id, position);
    }

    public void onSessionChanged() {
        long session = sessionGeneration.incrementAndGet();
        invalidate();
        ui.accept(() -> {
            if (disposed || view == null || sessionGeneration.get() != session) return;
            selected = null;
            detailVisible = false;
            positions.clear();
            titles.clear();
            view.clearSessionContent();
        });
    }

    private void invalidate() {
        requestGeneration.incrementAndGet();
        if (pendingLoad != null) pendingLoad.cancel(true);
        if (pendingNavigation != null) pendingNavigation.cancel(true);
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        invalidate();
        if (ownsExecutor) executor.shutdownNow();
        positions.clear();
        titles.clear();
        if (sessionSubscription != null) {
            try { sessionSubscription.close(); } catch (Exception ignored) { }
        }
    }
}
