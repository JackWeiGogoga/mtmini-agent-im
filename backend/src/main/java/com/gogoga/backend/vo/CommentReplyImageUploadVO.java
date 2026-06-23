package com.gogoga.backend.vo;

import lombok.Data;

/**
 * 评论回复测试图片上传结果。
 */
@Data
public class CommentReplyImageUploadVO {

    private String fileName;
    private String objectKey;
    private String url;
}
