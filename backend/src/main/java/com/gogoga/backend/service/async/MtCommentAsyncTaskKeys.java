package com.gogoga.backend.service.async;

/**
 * 评价处理异步任务幂等键。
 */
public final class MtCommentAsyncTaskKeys {

    private MtCommentAsyncTaskKeys() {
    }

    public static String autoReply(Long commentId) {
        return "comment:" + commentId;
    }

    public static String complaint(Long commentId) {
        return "comment-complaint:" + commentId;
    }

    public static String logAnalysis(Long commentId) {
        return "comment-log-analysis:" + commentId;
    }

    public static String imFollowUp(Long commentId) {
        return "comment-im-follow-up:" + commentId;
    }
}
