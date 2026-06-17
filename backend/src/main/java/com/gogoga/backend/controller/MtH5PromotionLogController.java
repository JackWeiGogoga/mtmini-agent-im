package com.gogoga.backend.controller;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresShopRight;
import com.gogoga.backend.constant.MtShopRightCodeConstant;
import com.gogoga.backend.service.MtH5ShopService;
import com.gogoga.backend.service.MtPromotionLogService;
import com.gogoga.backend.vo.MtH5PromotionLogVO;
import com.gogoga.backend.vo.ResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * H5 推广日志接口
 *
 * @author system
 * @since 2026-03-26
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/h5/promotion/logs")
@RequiresLogin
@RequiresShopRight(MtShopRightCodeConstant.H5_PROMOTION)
public class MtH5PromotionLogController {

    private final MtH5ShopService mtH5ShopService;
    private final MtPromotionLogService mtPromotionLogService;

    /**
     * 查询当前门店推广日志
     *
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 分页日志
     */
    @GetMapping
    public ResultVO<PageInfo<MtH5PromotionLogVO>> listLogs(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {

        return ResultVO.success(mtPromotionLogService.listLogs(
                mtH5ShopService.getCurrentShop(),
                pageNum,
                pageSize
        ));
    }

    /**
     * 标记当前门店推广日志已读
     *
     * @return 成功
     */
    @PostMapping("/read")
    public ResultVO<Boolean> markLogsRead() {
        mtPromotionLogService.markCurrentShopLogsRead(mtH5ShopService.getCurrentShop());
        return ResultVO.success(Boolean.TRUE);
    }
}
