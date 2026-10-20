package com.github.yuyuanweb.mianshiyaplugin.reader;

import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.*;

/** UI boundary; the navigation controller can be tested without a browser. */
public interface ReaderView {
    void showBanks();
    void showQuestions(Long bankId);
    void restoreQuestionList();
    void showLoading(QuestionSelection selection);
    void showDocument(RenderedQuestion document, ReadingPosition position);
    void showFailure(QuestionLoadResult result);
    void updateNavigation(boolean hasPrevious, boolean hasNext);
    void clearSessionContent();
}
