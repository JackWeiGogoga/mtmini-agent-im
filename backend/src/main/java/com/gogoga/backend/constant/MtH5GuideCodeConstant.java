package com.gogoga.backend.constant;

import java.util.Set;

/**
 * H5 引导编码常量
 *
 * @author system
 * @since 2026-05-12
 */
public final class MtH5GuideCodeConstant {

    public static final String HOME_FIRST_AUTH_GUIDE_V1 = "HOME_FIRST_AUTH_GUIDE_V1";

    public static final String HOME_FIRST_AUTH_GUIDE_V2 = "HOME_FIRST_AUTH_GUIDE_V2";

    public static final String CURRENT_HOME_FIRST_AUTH_GUIDE = HOME_FIRST_AUTH_GUIDE_V2;

    private static final Set<String> SUPPORTED_CODES = Set.of(
            HOME_FIRST_AUTH_GUIDE_V1,
            HOME_FIRST_AUTH_GUIDE_V2
    );

    private MtH5GuideCodeConstant() {
    }

    public static boolean isSupported(String guideCode) {
        return SUPPORTED_CODES.contains(guideCode);
    }
}
