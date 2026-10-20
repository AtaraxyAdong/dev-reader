package com.github.yuyuanweb.mianshiyaplugin.reader.data;

import com.github.yuyuanweb.mianshiyaplugin.adapter.DateTypeAdapter;
import com.github.yuyuanweb.mianshiyaplugin.api.MianShiYaApi;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.LoadStatus;
import com.google.gson.GsonBuilder;
import okhttp3.*;
import org.junit.Test;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real Retrofit request and JSON conversion with a deterministic in-memory HTTP transport. */
public class ApiQuestionRepositoryHttpTest {
    private static final String SUCCESS = "{\"code\":0,\"data\":{\"id\":12,\"title\":\"Question\",\"content\":\"Body\",\"contentType\":1}}";

    @Test public void sendsQuestionIdRequiredByLiveContract() {
        AtomicReference<Request> sent = new AtomicReference<>();
        MianShiYaApi api = api(chain -> {
            sent.set(chain.request());
            boolean valid = "12".equals(chain.request().url().queryParameter("questionId"));
            return reply(chain.request(), 200, valid ? SUCCESS : "{\"code\":50000}");
        });
        var result = new ApiQuestionRepository(api).loadQuestion(12L, null);
        assertEquals(LoadStatus.READY, result.getStatus());
        assertEquals("/api/question/get/vo", sent.get().url().encodedPath());
        assertEquals("12", sent.get().url().queryParameter("questionId"));
        assertNull(sent.get().url().queryParameter("id"));
        assertEquals("Body", result.getDocument().getBody().getContent());
    }

    @Test public void acceptsNullVipExpiryInAnswerAuthorAndLoginUser() throws Exception {
        String question = "{\"code\":0,\"data\":{\"id\":12,\"title\":\"Question\",\"content\":\"Body\",\"contentType\":1,\"bestQuestionAnswer\":{\"content\":\"Answer\",\"contentType\":1,\"user\":{\"id\":7,\"vipExpireTime\":null}}}}";
        MianShiYaApi api = api(chain -> reply(chain.request(), 200,
                chain.request().url().encodedPath().endsWith("user/get/login")
                ? "{\"code\":0,\"data\":{\"id\":7,\"vipExpireTime\":null}}" : question));
        assertEquals(LoadStatus.READY, new ApiQuestionRepository(api).loadQuestion(12L, null).getStatus());
        var user = api.getLoginUser().execute().body().getData();
        assertEquals(Long.valueOf(7), user.getId());
        assertNull(user.getVipExpireTime());
    }

    @Test public void reportsOnlySafeBusinessAndHttpCodes() {
        String privateMessage = "private-server-message";
        var business = new ApiQuestionRepository(api(chain -> reply(chain.request(), 200,
                "{\"code\":50000,\"message\":\"" + privateMessage + "\"}"))).loadQuestion(12L, null);
        assertEquals(LoadStatus.ERROR, business.getStatus());
        assertTrue(business.getMessage().contains("50000"));
        assertFalse(business.getMessage().contains(privateMessage));
        var http = new ApiQuestionRepository(api(chain -> reply(chain.request(), 503,
                privateMessage))).loadQuestion(12L, null);
        assertTrue(http.getMessage().contains("503"));
        assertFalse(http.getMessage().contains(privateMessage));
    }

    @Test public void distinguishesExpiredSessionAndPermissionFromMalformedData() {
        for (int code : new int[]{40100, 40110, 40101, 40102}) {
            var result = new ApiQuestionRepository(api(chain -> reply(chain.request(), 200,
                    "{\"code\":" + code + "}"))).loadQuestion(12L, null);
            assertEquals(code == 40100 || code == 40110 ? LoadStatus.NEEDS_LOGIN : LoadStatus.FORBIDDEN,
                    result.getStatus());
        }
        var malformed = new ApiQuestionRepository(api(chain -> reply(chain.request(), 200,
                "<html>private-error-page</html>"))).loadQuestion(12L, null);
        assertEquals(LoadStatus.ERROR, malformed.getStatus());
        assertTrue(malformed.getMessage().contains("解析"));
        assertFalse(malformed.getMessage().contains("private-error-page"));
    }

    private static MianShiYaApi api(Interceptor transport) {
        return new Retrofit.Builder().baseUrl("https://example.invalid/api/")
                .client(new OkHttpClient.Builder().addInterceptor(transport).build())
                .addConverterFactory(GsonConverterFactory.create(new GsonBuilder()
                        .registerTypeAdapter(Date.class, new DateTypeAdapter()).create()))
                .build().create(MianShiYaApi.class);
    }
    private static Response reply(Request request, int status, String body) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status)
                .message("test").body(ResponseBody.create(MediaType.parse("application/json"), body)).build();
    }
}
