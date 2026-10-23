package com.github.yuyuanweb.mianshiyaplugin.reader.data;

import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.intellij.openapi.diagnostic.Logger;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

/** Reads website data only; website scripts and styles are never executed or rendered. */
public final class QuestionPageSource {
    private static final int MAX_PAGE_BYTES = 4 * 1024 * 1024;
    private final OkHttpClient client;
    private final QuestionPageParser parser = new QuestionPageParser();

    public QuestionPageSource(OkHttpClient sessionClient) {
        // The session header must never follow a redirect to another origin.
        client = sessionClient.newBuilder().followRedirects(false).followSslRedirects(false)
                .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS).build();
    }

    public QuestionLoadResult load(long id) {
        if (id <= 0) return QuestionLoadResult.failure(LoadStatus.NOT_FOUND, "题目不存在");
        URI uri = URI.create("https://www.mianshiya.com/question/" + id);
        Request request = new Request.Builder().url(uri.toString())
                .header("Accept", "text/html").header("Cache-Control", "no-cache").build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 401) return QuestionLoadResult.failure(LoadStatus.NEEDS_LOGIN, "登录状态已失效");
            if (response.code() == 403) return QuestionLoadResult.failure(LoadStatus.FORBIDDEN, "无权查看此题");
            if (response.code() == 404) return QuestionLoadResult.failure(LoadStatus.NOT_FOUND, "题目不存在");
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                return QuestionLoadResult.failure(LoadStatus.ERROR, "题目页面请求失败（HTTP " + response.code() + "），请重试。");
            }
            if (body.contentLength() > MAX_PAGE_BYTES || body.source().request(MAX_PAGE_BYTES + 1L)) {
                return QuestionLoadResult.failure(LoadStatus.ERROR, "题目页面过大，请查看原文。");
            }
            return fromPage(parser.parse(body.string(), id), id, uri);
        } catch (JsonParseException | IllegalStateException | IllegalArgumentException e) {
            return QuestionLoadResult.failure(LoadStatus.ERROR, "题目页面数据解析失败，请重试或查看原文。");
        } catch (IOException e) {
            if (Thread.currentThread().isInterrupted()) return QuestionLoadResult.failure(LoadStatus.CANCELLED, "");
            Logger.getInstance(QuestionPageSource.class).warn(
                    "Reader website transport failure: " + e.getClass().getSimpleName());
            return QuestionLoadResult.failure(LoadStatus.ERROR, "题目页面网络请求失败，请重试。");
        }
    }

    private static QuestionLoadResult fromPage(JsonObject question, long id, URI uri) {
        Boolean authorized = bool(question, "hasVipAuth");
        if (authorized == null) throw new JsonParseException("Missing access state");
        if (!authorized) {
            boolean vip = Integer.valueOf(1).equals(number(question, "needVip"));
            return QuestionLoadResult.failure(vip ? LoadStatus.FORBIDDEN : LoadStatus.NEEDS_LOGIN,
                    vip ? "此题需要相应访问权限，请查看原文。" : "网页登录状态已失效，请重新登录。");
        }
        String title = string(question, "title");
        String content = string(question, "content");
        // The authoritative page often uses the title as the entire question prompt.
        ContentPart body = ApiQuestionRepository.part(content == null ? "" : content,
                number(question, "contentType"), uri);
        ContentPart answer;
        if (Boolean.FALSE.equals(bool(question, "hasBestAnswer"))) {
            answer = ApiQuestionRepository.part("", null, uri);
        } else if (question.has("bestQuestionAnswer") && question.get("bestQuestionAnswer").isJsonObject()) {
            JsonObject best = question.getAsJsonObject("bestQuestionAnswer");
            answer = ApiQuestionRepository.part(string(best, "content"), number(best, "contentType"), uri);
        } else {
            answer = ContentPart.unavailable(uri);
        }
        return QuestionLoadResult.ready(new QuestionDocument(id, title == null ? "" : title, body, answer, uri));
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new JsonParseException("Expected text");
        return value.getAsString();
    }
    private static Integer number(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new JsonParseException("Expected number");
        return value.getAsInt();
    }
    private static Boolean bool(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new JsonParseException("Expected boolean");
        return value.getAsBoolean();
    }
}
