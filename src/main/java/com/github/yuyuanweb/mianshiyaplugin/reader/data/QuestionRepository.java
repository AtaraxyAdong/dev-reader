package com.github.yuyuanweb.mianshiyaplugin.reader.data;

import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.NavigationContext;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.QuestionLoadResult;

/** Reader-facing question and navigation data access. */
public interface QuestionRepository {
    QuestionLoadResult loadQuestion(long questionId, Long bankId);

    NavigationContext loadNavigation(NavigationContext current);
}
