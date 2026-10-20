package com.github.yuyuanweb.mianshiyaplugin.reader.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.junit.Test;

import static org.junit.Assert.*;

/** Synthetic Flight fixtures contain no site article content. */
public class QuestionPageParserTest {
    private final QuestionPageParser parser = new QuestionPageParser();

    @Test public void readsLiteralDetailWithoutResolvingUnrelatedReactReferences() {
        JsonObject question = parser.parse(page("a:I[\"module\"]\n0:[\"$\",\"$La\",null,{\"initialQuestionDetail\":{\"id\":\"2031664941567598594\",\"title\":\"Example\",\"hasVipAuth\":true,\"bestQuestionAnswer\":{\"content\":\"## Answer\",\"contentType\":1}}}]\n"), 2031664941567598594L);
        assertEquals("## Answer", question.getAsJsonObject("bestQuestionAnswer").get("content").getAsString());
        assertTrue(question.get("hasVipAuth").getAsBoolean());
    }

    @Test public void resolvesUtf8TextByByteLengthAcrossScriptChunks() {
        // 18 UTF-8 bytes; the apparent record inside the content must stay text.
        String flight = "0:{\"initialQuestionDetail\":\"$a\"}\na:{\"id\":12,\"bestQuestionAnswer\":\"$b\"}\nb:{\"content\":\"$c\",\"contentType\":1}\nc:T12,中文\n0:{\"id\":99}";
        String html = page(flight.substring(0, 41), flight.substring(41, 100), flight.substring(100));
        assertEquals("中文\n0:{\"id\":99}", parser.parse(html, 12).getAsJsonObject("bestQuestionAnswer").get("content").getAsString());
    }

    @Test public void preservesEscapedDollarAndUndefinedAsAbsentValue() {
        JsonObject question = parser.parse(page("0:{\"initialQuestionDetail\":{\"id\":12,\"content\":\"$$abc\",\"bestQuestionAnswer\":\"$undefined\"}}\n"), 12);
        assertEquals("$abc", question.get("content").getAsString());
        assertTrue(question.get("bestQuestionAnswer").isJsonNull());
    }

    @Test public void doesNotReinterpretLiteralTextFrameAsAReference() {
        assertEquals("$ff", parser.parse(page("0:{\"initialQuestionDetail\":{\"id\":12,\"content\":\"$a\"}}\na:T3,$ff"), 12).get("content").getAsString());
    }

    @Test public void rejectsMissingContentReference() { rejects(page("0:{\"initialQuestionDetail\":{\"id\":12,\"content\":\"$ff\"}}\n"), 12); }
    @Test public void rejectsReferenceCycle() { rejects(page("0:{\"initialQuestionDetail\":\"$a\"}\na:{\"id\":12,\"bestQuestionAnswer\":\"$b\"}\nb:{\"content\":\"$a\"}\n"), 12); }
    @Test public void rejectsWrongQuestionIdWithoutRoundingLongs() { rejects(page("0:{\"initialQuestionDetail\":{\"id\":2031664941567598593}}\n"), 2031664941567598594L); }
    @Test public void rejectsFractionalQuestionId() { rejects(page("0:{\"initialQuestionDetail\":{\"id\":12.5}}\n"), 12); }
    @Test public void rejectsTruncatedTextFrame() { rejects(page("0:{\"initialQuestionDetail\":{\"id\":12,\"content\":\"$a\"}}\na:T10,short"), 12); }
    @Test public void rejectsMalformedJsonRatherThanEvaluatingJavascript() { rejects("<script>self.__next_f.push([1, (function(){throw 'do not execute';})()]);</script>", 12); }
    @Test public void rejectsMissingPageData() { rejects("<html><body>Login or verification required</body></html>", 12); }
    @Test public void ignoresMarkupThatMerelyMentionsFlightCalls() { rejects("<p>self.__next_f.push([1,\"0:{}\\n\"])</p>", 12); }
    @Test public void rejectsDeeplyNestedPayload() {
        String nested = "{\"id\":12}";
        for (int i = 0; i < 150; i++) nested = "{\"nested\":" + nested + "}";
        rejects(page("0:{\"initialQuestionDetail\":" + nested + "}\n"), 12);
    }

    @Test public void ignoresUnrelatedReferencesInsideQuestionAndAnswerMetadata() {
        JsonObject question = parser.parse(page("0:{\"initialQuestionDetail\":\"$a\"}\na:{\"id\":12,\"user\":\"$dead\",\"bestQuestionAnswer\":\"$b\"}\nb:{\"content\":\"Answer\",\"contentType\":1,\"user\":\"$beef\"}\n"), 12);
        assertEquals("Answer", question.getAsJsonObject("bestQuestionAnswer").get("content").getAsString());
    }
    private void rejects(String html, long id) {
        try { parser.parse(html, id); fail("Expected invalid or unavailable Flight detail to be rejected"); }
        catch (JsonParseException expected) { /* Fail closed without executing site scripts. */ }
    }
    private static String page(String... chunks) {
        StringBuilder html = new StringBuilder("<html>");
        for (String chunk : chunks) html.append("<script>self.__next_f.push(").append(new Gson().toJson(new Object[]{1, chunk})).append(");</script>");
        return html.append("</html>").toString();
    }
}
