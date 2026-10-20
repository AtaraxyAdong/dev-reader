package com.github.yuyuanweb.mianshiyaplugin.reader.model;

import lombok.Getter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable contracts shared by the API adapter, controller and views. */
public final class ReaderModels {
    private ReaderModels() { }
    public enum SourceFormat { MARKDOWN, HTML, UNKNOWN }
    public enum PartStatus { AVAILABLE, EMPTY, FORBIDDEN, UNAVAILABLE, ERROR }
    public enum LoadStatus { READY, NEEDS_LOGIN, FORBIDDEN, NOT_FOUND, ERROR, CANCELLED }

    @Getter
    public static final class ContentPart {
        private final PartStatus status;
        private final SourceFormat format;
        private final String content;
        private final URI baseUri;
        public ContentPart(PartStatus status, SourceFormat format, String content, URI baseUri) {
            this.status = Objects.requireNonNull(status);
            this.format = Objects.requireNonNull(format);
            this.content = status == PartStatus.AVAILABLE && content != null ? content : "";
            this.baseUri = Objects.requireNonNull(baseUri);
        }
        public static ContentPart unavailable(URI baseUri) {
            return new ContentPart(PartStatus.UNAVAILABLE, SourceFormat.UNKNOWN, "", baseUri);
        }
    }

    @Getter
    public static final class QuestionDocument {
        private final long id;
        private final String title;
        private final ContentPart body;
        private final ContentPart referenceAnswer;
        private final URI sourceUri;
        public QuestionDocument(long id, String title, ContentPart body, ContentPart referenceAnswer, URI sourceUri) {
            this.id = id;
            this.title = Objects.requireNonNull(title);
            this.body = Objects.requireNonNull(body);
            this.referenceAnswer = Objects.requireNonNull(referenceAnswer);
            this.sourceUri = Objects.requireNonNull(sourceUri);
        }
    }

    @Getter
    public static final class QuestionLoadResult {
        private final LoadStatus status;
        private final QuestionDocument document;
        private final String message;
        public QuestionLoadResult(LoadStatus status, QuestionDocument document, String message) {
            this.status = Objects.requireNonNull(status);
            if (status == LoadStatus.READY && document == null) throw new IllegalArgumentException("READY requires a document");
            this.document = status == LoadStatus.READY ? document : null;
            this.message = message == null ? "" : message;
        }
        public static QuestionLoadResult ready(QuestionDocument document) {
            return new QuestionLoadResult(LoadStatus.READY, document, "");
        }
        public static QuestionLoadResult failure(LoadStatus status, String message) {
            return new QuestionLoadResult(status, null, message);
        }
    }

    @Getter
    public static final class NavigationContext {
        private final Long bankId;
        private final List<Long> orderedQuestionIds;
        private final long selectedId;
        private final boolean bankOrderResolved;
        public NavigationContext(Long bankId, List<Long> orderedQuestionIds, long selectedId) {
            this(bankId, orderedQuestionIds, selectedId, false);
        }
        public NavigationContext(Long bankId, List<Long> orderedQuestionIds, long selectedId, boolean bankOrderResolved) {
            this.bankId = bankId;
            this.orderedQuestionIds = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(orderedQuestionIds)));
            this.selectedId = selectedId;
            this.bankOrderResolved = bankOrderResolved;
        }
    }

    @Getter
    public static final class QuestionSelection {
        private final long id;
        private final String title;
        private final NavigationContext navigation;
        public QuestionSelection(long id, String title, NavigationContext navigation) {
            this.id = id;
            this.title = Objects.requireNonNull(title);
            this.navigation = Objects.requireNonNull(navigation);
        }
    }

    @Getter
    public static final class ReadingPosition {
        public static final ReadingPosition TOP = new ReadingPosition("", 0);
        private final String blockId;
        private final double offsetRatio;
        public ReadingPosition(String blockId, double offsetRatio) {
            this.blockId = blockId == null ? "" : blockId;
            this.offsetRatio = Double.isFinite(offsetRatio) ? Math.max(0, Math.min(1, offsetRatio)) : 0;
        }
    }

    @Getter
    public static final class RenderedQuestion {
        private final long id;
        private final String html;
        private final URI sourceUri;
        public RenderedQuestion(long id, String html, URI sourceUri) {
            this.id = id;
            this.html = Objects.requireNonNull(html);
            this.sourceUri = Objects.requireNonNull(sourceUri);
        }
    }
}
