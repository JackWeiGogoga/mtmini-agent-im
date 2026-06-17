package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * H5 底部 tab 状态
 *
 * @author system
 * @since 2026-05-11
 */
@Data
public class MtH5TabStatusVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Boolean logUnread;

    private Boolean powerInsufficient;

    private String powerStatusText;
}
