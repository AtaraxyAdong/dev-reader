package com.github.yuyuanweb.mianshiyaplugin.reader.data;

import com.github.yuyuanweb.mianshiyaplugin.model.response.QuestionAnswer;

/** Subset of the public question VO consumed by the reader. */
public final class QuestionDetailResponse {
    private Long id;
    private String title;
    private String content;
    private Integer contentType;
    private QuestionAnswer bestQuestionAnswer;

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public Integer getContentType() { return contentType; }
    public QuestionAnswer getBestQuestionAnswer() { return bestQuestionAnswer; }

    // Package-visible constructor and setters keep tests independent of network access.
    QuestionDetailResponse(Long id, String title, String content, Integer contentType,
                           QuestionAnswer bestQuestionAnswer) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.contentType = contentType;
        this.bestQuestionAnswer = bestQuestionAnswer;
    }
}
