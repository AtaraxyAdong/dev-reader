package com.github.yuyuanweb.mianshiyaplugin.reader.data;

import com.github.yuyuanweb.mianshiyaplugin.api.MianShiYaApi;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.*;
import com.google.gson.Gson;
import okhttp3.*;
import org.junit.Test;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class QuestionPageSourceTest {
    @Test public void metadataOnlyApiLoadsAuthorizedPageAnswer() {
        AtomicInteger pages = new AtomicInteger();
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("Cookie", "SESSION=synthetic-test-session").build()))
                .addInterceptor(chain -> {
            Request request = chain.request();
            if (request.url().host().equals("api.mianshiya.com")) {
                return reply(request, 200, "{\"code\":0,\"data\":{\"id\":12,\"title\":\"Title\",\"hasVipAuth\":true}}");
            }
            pages.incrementAndGet();
            assertEquals("SESSION=synthetic-test-session", request.header("Cookie"));
            assertEquals("https://www.mianshiya.com/question/12", request.url().toString());
            return reply(request, 200, page(true, 0, true));
        }).build();
        MianShiYaApi api = new Retrofit.Builder().baseUrl("https://api.mianshiya.com/api/")
                .client(client).addConverterFactory(GsonConverterFactory.create()).build().create(MianShiYaApi.class);
        var result = new ApiQuestionRepository(api, new QuestionPageSource(client)).loadQuestion(12, null);
        assertEquals(LoadStatus.READY, result.getStatus());
        assertEquals(PartStatus.EMPTY, result.getDocument().getBody().getStatus());
        assertEquals("## Synthetic answer", result.getDocument().getReferenceAnswer().getContent());
        assertEquals(SourceFormat.MARKDOWN, result.getDocument().getReferenceAnswer().getFormat());
        assertEquals(1, pages.get());
    }
    @Test public void neverPresentsGuestOrVipPreviewAsFullAnswer() throws Exception {
        assertEquals(LoadStatus.NEEDS_LOGIN, source(200, page(false, 0, true)).load(12).getStatus());
        assertEquals(LoadStatus.FORBIDDEN, source(200, page(false, 1, true)).load(12).getStatus());
        assertEquals(LoadStatus.ERROR, source(200, page(true, 0, true).replace("\\\"hasVipAuth\\\":true,", "")).load(12).getStatus());
    }
    @Test public void distinguishesNoAnswerFromUnavailableAnswer() throws Exception {
        var absent = source(200, page(true, 0, false)).load(12);
        assertEquals(PartStatus.EMPTY, absent.getDocument().getReferenceAnswer().getStatus());
        String missing = page(true, 0, true).replace("\\\"content\\\":\\\"## Synthetic answer\\\",", "");
        assertEquals(PartStatus.UNAVAILABLE, source(200, missing).load(12).getDocument().getReferenceAnswer().getStatus());
    }
    @Test public void reportsHttpFailures() throws Exception {
        assertEquals(LoadStatus.NEEDS_LOGIN, source(401, "private").load(12).getStatus());
        assertEquals(LoadStatus.FORBIDDEN, source(403, "private").load(12).getStatus());
        assertEquals(LoadStatus.NOT_FOUND, source(404, "private").load(12).getStatus());
    }
    @Test public void doesNotForwardSessionToRedirectDestination() throws Exception {
        AtomicInteger redirected = new AtomicInteger();
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/start", exchange -> {
            exchange.getResponseHeaders().add("Location", "/destination");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/destination", exchange -> {
            redirected.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            String local = "http://127.0.0.1:" + server.getAddress().getPort() + "/start";
            OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> chain.proceed(
                    chain.request().newBuilder().url(local).header("Cookie", "SESSION=synthetic").build())).build();
            var result = new QuestionPageSource(client).load(12);
            assertEquals(LoadStatus.ERROR, result.getStatus());
            assertTrue(result.getMessage().contains("302"));
            assertEquals(0, redirected.get());
        } finally {
            server.stop(0);
        }
    }
    @Test public void rejectsNonPageAndOversizedResponse() throws Exception {
        assertEquals(LoadStatus.ERROR, source(200, "<html>challenge</html>").load(12).getStatus());
        assertEquals(LoadStatus.ERROR, source(200, "x".repeat(4 * 1024 * 1024 + 1)).load(12).getStatus());
    }
    @Test public void fallsBackToAuthorizedPageWhenMetadataNetworkFails() {
        MianShiYaApi api = failingApi(false);
        var result = new ApiQuestionRepository(api, source(200, page(true, 0, true))).loadQuestion(12, null);
        assertEquals(LoadStatus.READY, result.getStatus());
        assertEquals("## Synthetic answer", result.getDocument().getReferenceAnswer().getContent());
    }
    @Test public void fallbackStillHonorsWebsiteAccessDecision() {
        var result = new ApiQuestionRepository(failingApi(false), source(200, page(false, 1, true))).loadQuestion(12, null);
        assertEquals(LoadStatus.FORBIDDEN, result.getStatus());
        assertNull(result.getDocument());
    }
    @Test public void canceledRequestDoesNotStartPageFetch() {
        AtomicInteger pageCalls = new AtomicInteger();
        QuestionPageSource pages = new QuestionPageSource(new OkHttpClient.Builder().addInterceptor(chain -> {
            pageCalls.incrementAndGet();
            return reply(chain.request(), 200, page(true, 0, true));
        }).build());
        try {
            var result = new ApiQuestionRepository(failingApi(true), pages).loadQuestion(12, null);
            assertEquals(LoadStatus.CANCELLED, result.getStatus());
            assertEquals(0, pageCalls.get());
        } finally { Thread.interrupted(); }
    }
    @Test public void explicitApiDenialNeverFallsBackToPage() {
        for (int code : new int[]{40100, 40101, 40102, 40110}) {
            AtomicInteger pageCalls = new AtomicInteger();
            OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
                if (chain.request().url().host().equals("api.mianshiya.com"))
                    return reply(chain.request(), 200, "{\"code\":" + code + "}");
                pageCalls.incrementAndGet();
                return reply(chain.request(), 200, page(true, 0, true));
            }).build();
            MianShiYaApi api = new Retrofit.Builder().baseUrl("https://api.mianshiya.com/api/")
                    .client(client).addConverterFactory(GsonConverterFactory.create()).build().create(MianShiYaApi.class);
            var result = new ApiQuestionRepository(api, new QuestionPageSource(client)).loadQuestion(12, null);
            assertNotEquals(LoadStatus.READY, result.getStatus());
            assertEquals(0, pageCalls.get());
        }
    }
    private static MianShiYaApi failingApi(boolean interrupt) {
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            if (interrupt) Thread.currentThread().interrupt();
            throw new java.net.SocketTimeoutException("synthetic timeout");
        }).build();
        return new Retrofit.Builder().baseUrl("https://api.mianshiya.com/api/")
                .client(client).addConverterFactory(GsonConverterFactory.create()).build().create(MianShiYaApi.class);
    }
    private static QuestionPageSource source(int status, String body) {
        return new QuestionPageSource(new OkHttpClient.Builder().addInterceptor(chain -> reply(chain.request(), status, body)).build());
    }
    private static Response reply(Request request, int status, String body) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status)
                .message("test").body(ResponseBody.create(MediaType.parse("text/html; charset=utf-8"), body)).build();
    }
    private static String page(boolean authorized, int vip, boolean hasAnswer) {
        String question = "{\"id\":\"12\",\"title\":\"Title\",\"contentType\":1,\"hasVipAuth\":" + authorized
                + ",\"needVip\":" + vip + ",\"hasBestAnswer\":" + hasAnswer
                + (hasAnswer ? ",\"bestQuestionAnswer\":{\"content\":\"## Synthetic answer\",\"contentType\":1}" : "") + "}";
        String flight = "0:[\"$\",\"div\",null,{\"initialQuestionDetail\":" + question + "}]\n";
        return "<script>self.__next_f.push(" + new Gson().toJson(new Object[]{1, flight}) + ")</script>";
    }
}
