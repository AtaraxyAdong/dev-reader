package com.github.yuyuanweb.mianshiyaplugin.reader.data;

import com.github.yuyuanweb.mianshiyaplugin.model.response.QuestionAnswer;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.LoadStatus;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.NavigationContext;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.PartStatus;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.SourceFormat;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Synthetic mapper tests; these are not live response fixtures. */
public class ApiQuestionRepositoryTest {

    @Test
    public void mapsCompleteQuestion() {
        QuestionAnswer answer = answer("## Answer", 1);
        QuestionDetailResponse response = new QuestionDetailResponse(12L, "Title", "<p>Body</p>", 0,
                answer);

        var document = ApiQuestionRepository.toDocument(response, 12L);

        assertEquals("Title", document.getTitle());
        assertEquals("<p>Body</p>", document.getBody().getContent());
        assertEquals(SourceFormat.HTML, document.getBody().getFormat());
        assertEquals("## Answer", document.getReferenceAnswer().getContent());
        assertEquals(SourceFormat.MARKDOWN, document.getReferenceAnswer().getFormat());
        assertTrue(document.getSourceUri().toString().endsWith("/question/12"));
    }

    @Test
    public void keepsBodyWhenAnswerIsOmitted() {
        QuestionDetailResponse response = new QuestionDetailResponse(12L, "Title", "Body", 0,
                null);

        var document = ApiQuestionRepository.toDocument(response, 12L);

        assertEquals(PartStatus.AVAILABLE, document.getBody().getStatus());
        assertEquals("Body", document.getBody().getContent());
        assertEquals(PartStatus.UNAVAILABLE, document.getReferenceAnswer().getStatus());
    }

    @Test
    public void distinguishesMissingAndExplicitlyEmptyAnswer() {
        QuestionDetailResponse missing = new QuestionDetailResponse(12L, "Title", "Body", 0,
                null);
        QuestionAnswer emptyAnswer = answer("", 1);
        QuestionDetailResponse presentButEmpty = new QuestionDetailResponse(12L, "Title", "Body", 0,
                emptyAnswer);

        assertEquals(PartStatus.UNAVAILABLE,
                ApiQuestionRepository.toDocument(missing, 12L).getReferenceAnswer().getStatus());
        assertEquals(PartStatus.EMPTY,
                ApiQuestionRepository.toDocument(presentButEmpty, 12L).getReferenceAnswer().getStatus());
    }

    @Test
    public void keepsUnknownFormatWithoutReclassifyingContent() {
        QuestionDetailResponse response = new QuestionDetailResponse(12L, "Title", "<p>Body</p>", null,
                null);

        var body = ApiQuestionRepository.toDocument(response, 12L).getBody();

        assertEquals(PartStatus.AVAILABLE, body.getStatus());
        assertEquals(SourceFormat.UNKNOWN, body.getFormat());
        assertEquals("<p>Body</p>", body.getContent());
    }

    @Test
    public void mapsExpiredSessionAndPermissionCodesForSilentReaderResults() {
        assertEquals(LoadStatus.NEEDS_LOGIN, ApiQuestionRepository.statusForApplicationCode(40100));
        assertEquals(LoadStatus.NEEDS_LOGIN, ApiQuestionRepository.statusForApplicationCode(40110));
        assertEquals(LoadStatus.FORBIDDEN, ApiQuestionRepository.statusForApplicationCode(40101));
        assertEquals(LoadStatus.FORBIDDEN, ApiQuestionRepository.statusForApplicationCode(40102));
        assertFalse(ApiQuestionRepository.statusForApplicationCode(50000) == LoadStatus.NEEDS_LOGIN);
    }

    @Test
    public void preservesCurrentNavigationWhenServerIdsAreInvalid() {
        NavigationContext current = new NavigationContext(7L, java.util.Arrays.asList(10L, 12L), 12L);

        assertSame(current, ApiQuestionRepository.withValidatedNavigation(current,
                java.util.Arrays.asList(1L, 2L)));
        assertSame(current, ApiQuestionRepository.withValidatedNavigation(current,
                java.util.Arrays.asList(10L, null, 12L)));
    }

    @Test
    public void acceptsValidatedNavigationContainingSelectedQuestion() {
        NavigationContext current = new NavigationContext(7L, java.util.Arrays.asList(10L, 12L), 12L);

        NavigationContext updated = ApiQuestionRepository.withValidatedNavigation(current,
                java.util.Arrays.asList(11L, 12L, 13L));

        assertEquals(java.util.Arrays.asList(11L, 12L, 13L), updated.getOrderedQuestionIds());
        assertEquals(12L, updated.getSelectedId());
        assertEquals(Long.valueOf(7L), updated.getBankId());
    }

    private static QuestionAnswer answer(String content, int contentType) {
        QuestionAnswer answer = new QuestionAnswer();
        answer.setContent(content);
        answer.setContentType(contentType);
        return answer;
    }
}
