package com.gogoga.backend.controller;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresShopRight;
import com.gogoga.backend.constant.MtShopRightCodeConstant;
import com.gogoga.backend.service.MtH5CommentReplyLogService;
import com.gogoga.backend.service.MtH5ShopService;
import com.gogoga.backend.vo.MtH5CommentReplyLogVO;
import com.gogoga.backend.vo.ResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * H5 评价自动回复日志接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/h5/comment-reply/logs")
@RequiresLogin
@RequiresShopRight({MtShopRightCodeConstant.VIP_CODE, MtShopRightCodeConstant.H5_PROMOTION})
public class MtH5CommentReplyLogController {

    private final MtH5ShopService mtH5ShopService;
    private final MtH5CommentReplyLogService mtH5CommentReplyLogService;

    @GetMapping
    public ResultVO<PageInfo<MtH5CommentReplyLogVO>> listLogs(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return ResultVO.success(mtH5CommentReplyLogService.listLogs(
                mtH5ShopService.getCurrentShop(),
                pageNum,
                pageSize
        ));
    }
}
