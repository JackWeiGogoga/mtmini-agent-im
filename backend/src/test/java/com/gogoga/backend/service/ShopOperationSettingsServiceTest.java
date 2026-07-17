package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtCommentReplySetting;
import com.gogoga.backend.mapper.MtCommentReplySettingHistoryMapper;
import com.gogoga.backend.mapper.MtCommentReplySettingMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.gogoga.backend.vo.ShopOperationSettingsVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopOperationSettingsServiceTest {

    @Mock
    private MtShopMapper mtShopMapper;
    @Mock
    private MtCommentReplySettingMapper mtCommentReplySettingMapper;
    @Mock
    private MtCommentReplySettingHistoryMapper mtCommentReplySettingHistoryMapper;

    @InjectMocks
    private ShopOperationSettingsService service;

    @Test
    void buildSettingsEnablesBothSwitchesByDefaultWhenNoSettingExists() {
        when(mtCommentReplySettingMapper.selectByShopId(100L)).thenReturn(null);

        ShopOperationSettingsVO settings = service.buildSettings(100L);

        assertTrue(settings.getAutoReplyEnabled());
        assertTrue(settings.getNegativeCommentAppealEnabled());
    }

    @Test
    void buildSettingsPreservesSavedNegativeCommentAppealChoice() {
        MtCommentReplySetting saved = new MtCommentReplySetting();
        saved.setShopId(100L);
        saved.setAutoReplyEnabled(true);
        saved.setNegativeCommentAppealEnabled(false);
        when(mtCommentReplySettingMapper.selectByShopId(100L)).thenReturn(saved);

        ShopOperationSettingsVO settings = service.buildSettings(100L);

        assertTrue(settings.getAutoReplyEnabled());
        assertFalse(settings.getNegativeCommentAppealEnabled());
    }
}
