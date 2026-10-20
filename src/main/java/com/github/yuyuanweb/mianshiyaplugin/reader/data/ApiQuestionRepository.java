package com.github.yuyuanweb.mianshiyaplugin.reader.data;

import com.github.yuyuanweb.mianshiyaplugin.api.MianShiYaApi;
import com.github.yuyuanweb.mianshiyaplugin.config.ApiConfig;
import com.github.yuyuanweb.mianshiyaplugin.constant.CommonConstant;
import com.github.yuyuanweb.mianshiyaplugin.model.common.BaseResponse;
import com.github.yuyuanweb.mianshiyaplugin.model.dto.DoQuestionInfoVO;
import com.github.yuyuanweb.mianshiyaplugin.model.response.QuestionAnswer;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.ContentPart;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.LoadStatus;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.NavigationContext;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.PartStatus;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.QuestionDocument;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.QuestionLoadResult;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.SourceFormat;
import com.google.gson.JsonParseException;
import com.google.gson.stream.MalformedJsonException;
import java.io.EOFException;
import retrofit2.Response;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** API-backed reader data adapter. It never opens login UI or logs response data. */
public final class ApiQuestionRepository implements QuestionRepository {
    private static final int CODE_NOT_LOGIN = 40100;
    private static final int CODE_NO_ACCESS = 40101;
    private static final int CODE_NO_VIP_AUTH = 40102;
    private static final int CODE_LOGIN_CONFLICT = 40110;
    private final MianShiYaApi api;

    public ApiQuestionRepository() {
        this(ApiConfig.readerMianShiYaApi);
    }

    ApiQuestionRepository(MianShiYaApi api) {
        if (api == null) throw new IllegalArgumentException("api is required");
        this.api = api;
    }

    @Override
    public QuestionLoadResult loadQuestion(long questionId, Long bankId) {
        try {
            Response<BaseResponse<QuestionDetailResponse>> http = api.getReaderQuestionDetail(questionId).execute();
            if (http.code() == 401 || http.code() == 403) {
                return QuestionLoadResult.failure(http.code() == 401 ? LoadStatus.NEEDS_LOGIN : LoadStatus.FORBIDDEN,
                        http.code() == 401 ? "登录状态已失效" : "无权查看此题");
            }
            if (http.code() == 404) return QuestionLoadResult.failure(LoadStatus.NOT_FOUND, "题目不存在");
            if (!http.isSuccessful() || http.body() == null) {
                return QuestionLoadResult.failure(LoadStatus.ERROR, "题目请求失败（HTTP " + http.code() + "），请重试。");
            }
            BaseResponse<QuestionDetailResponse> body = http.body();
            LoadStatus codeStatus = statusForApplicationCode(body.getCode());
            if (codeStatus != null) return QuestionLoadResult.failure(codeStatus,
                    codeStatus == LoadStatus.NEEDS_LOGIN ? "登录状态已失效" : "无权查看此题");
            if (body.getCode() != 0) return QuestionLoadResult.failure(LoadStatus.ERROR,
                    "题目接口返回错误（业务码 " + body.getCode() + "），请重试。");
            QuestionDetailResponse question = body.getData();
            if (question == null || question.getId() == null || question.getId() != questionId) {
                return QuestionLoadResult.failure(LoadStatus.NOT_FOUND, "题目不存在");
            }
            return QuestionLoadResult.ready(toDocument(question, questionId));
        } catch (JsonParseException | IllegalStateException | MalformedJsonException | EOFException e) {
            return QuestionLoadResult.failure(LoadStatus.ERROR, "题目数据解析失败，请重试。");
        } catch (IOException e) {
            return QuestionLoadResult.failure(LoadStatus.ERROR, "题目网络请求失败，请重试。");
        } catch (RuntimeException e) {
            return QuestionLoadResult.failure(LoadStatus.ERROR, "题目加载失败，请重试。");
        }
    }

    @Override
    public NavigationContext loadNavigation(NavigationContext context) {
        if (context == null || context.getBankId() == null) return context;
        try {
            Response<BaseResponse<DoQuestionInfoVO>> response = api
                    .getDoQuestionInfo(context.getBankId(), context.getSelectedId()).execute();
            if (!response.isSuccessful() || response.body() == null || response.body().getCode() != 0
                    || response.body().getData() == null) return context;
            return withValidatedNavigation(context, response.body().getData().getQuestionIdList());
        } catch (IOException | RuntimeException e) {
            return context;
        }
    }

    static QuestionDocument toDocument(QuestionDetailResponse question, long requestedId) {
        URI sourceUri = URI.create(CommonConstant.WEB_HOST + "question/" + requestedId);
        ContentPart body = part(question.getContent(), question.getContentType(), sourceUri);
        QuestionAnswer answer = question.getBestQuestionAnswer();
        ContentPart referenceAnswer;
        if (answer != null) {
            referenceAnswer = part(answer.getContent(), answer.getContentType(), sourceUri);
        } else {
            referenceAnswer = ContentPart.unavailable(sourceUri);
        }
        return new QuestionDocument(requestedId, question.getTitle() == null ? "" : question.getTitle(),
                body, referenceAnswer, sourceUri);
    }

    private static ContentPart part(String content, Integer contentType, URI baseUri) {
        if (content == null) return ContentPart.unavailable(baseUri);
        if (content.isEmpty()) return new ContentPart(PartStatus.EMPTY, SourceFormat.UNKNOWN, "", baseUri);
        SourceFormat format;
        if (Integer.valueOf(0).equals(contentType)) format = SourceFormat.HTML;
        else if (Integer.valueOf(1).equals(contentType)) format = SourceFormat.MARKDOWN;
        else format = SourceFormat.UNKNOWN;
        return new ContentPart(PartStatus.AVAILABLE, format, content, baseUri);
    }

    static LoadStatus statusForApplicationCode(int code) {
        if (code == CODE_NOT_LOGIN || code == CODE_LOGIN_CONFLICT) return LoadStatus.NEEDS_LOGIN;
        if (code == CODE_NO_ACCESS || code == CODE_NO_VIP_AUTH) return LoadStatus.FORBIDDEN;
        return null;
    }

    static NavigationContext withValidatedNavigation(NavigationContext current, List<Long> candidateIds) {
        if (current == null) return null;
        List<Long> ids = validatedIds(candidateIds, current.getSelectedId());
        return ids.isEmpty() ? current
                : new NavigationContext(current.getBankId(), ids, current.getSelectedId(), true);
    }

    private static List<Long> validatedIds(List<Long> ids, long selectedId) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        Set<Long> seen = new HashSet<>();
        List<Long> result = new ArrayList<>(ids.size());
        for (Long id : ids) {
            if (id == null || !seen.add(id)) return Collections.emptyList();
            result.add(id);
        }
        return seen.contains(selectedId) ? Collections.unmodifiableList(result) : Collections.emptyList();
    }
}
