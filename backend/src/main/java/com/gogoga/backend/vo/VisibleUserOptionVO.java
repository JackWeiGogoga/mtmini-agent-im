package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 可见用户筛选项
 */
@Data
public class VisibleUserOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String userId;

    private String username;

    private String nickname;

    private String displayName;
}
