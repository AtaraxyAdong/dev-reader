package com.github.yuyuanweb.mianshiyaplugin.reader;

import com.github.yuyuanweb.mianshiyaplugin.model.dto.QuestionQueryRequest;
import com.github.yuyuanweb.mianshiyaplugin.view.QuestionListManager;
import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class ReaderNavigationTest {
    @Test public void bankFilteredSearchKeepsResultPageNavigation() {
        QuestionQueryRequest request = new QuestionQueryRequest();
        request.setQuestionBankId(7L); request.setTitle("ConcurrentHashMap");
        assertNull(QuestionListManager.navigationBankId(request));
    }
    @Test public void filteredBankDoesNotEscapeTagOrDifficulty() {
        QuestionQueryRequest request = new QuestionQueryRequest(); request.setQuestionBankId(7L);
        request.setTagList(Collections.singletonList("Java"));
        assertNull(QuestionListManager.navigationBankId(request));
        request.setTagList(null); request.setDifficulty(3);
        assertNull(QuestionListManager.navigationBankId(request));
    }
    @Test public void plainBankBrowserUsesAuthoritativeBankOrder() {
        QuestionQueryRequest request = new QuestionQueryRequest(); request.setQuestionBankId(7L);
        assertEquals(Long.valueOf(7), QuestionListManager.navigationBankId(request));
    }
}
