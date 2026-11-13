package com.gogoga.backend.controller;

import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresShopRight;
import com.gogoga.backend.constant.MtShopRightCodeConstant;
import com.gogoga.backend.param.KnowledgeQaParam;
import com.gogoga.backend.service.KnowledgeService;
import com.gogoga.backend.vo.KnowledgeItemVO;
import com.gogoga.backend.vo.ResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * H5 门店知识库接口。
 */
@RestController
@RequestMapping("/api/h5/knowledge/items")
@RequiredArgsConstructor
@RequiresLogin
// 知识库是自动回复的组成部分，权益范围必须与 MtShopRightService#hasAutoReplyRight 保持一致。
@RequiresShopRight({MtShopRightCodeConstant.VIP_CODE, MtShopRightCodeConstant.H5_PROMOTION,
        MtShopRightCodeConstant.IM_AUTO_REPLY_ONLY})
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @GetMapping
    public ResultVO<List<KnowledgeItemVO>> listQa() {
        return ResultVO.success(knowledgeService.listCurrentShopQa());
    }

    @PostMapping
    public ResultVO<KnowledgeItemVO> createQa(@Valid @RequestBody KnowledgeQaParam param) {
        return ResultVO.success(knowledgeService.createCurrentShopQa(param));
    }

    @PutMapping("/{id}")
    public ResultVO<KnowledgeItemVO> updateQa(
            @PathVariable Long id,
            @Valid @RequestBody KnowledgeQaParam param
    ) {
        return ResultVO.success(knowledgeService.updateCurrentShopQa(id, param));
    }

    @DeleteMapping("/all")
    public ResultVO<Void> clearAllKnowledge() {
        knowledgeService.clearCurrentShopKnowledge();
        return ResultVO.success();
    }

    @DeleteMapping("/{id}")
    public ResultVO<Void> deleteQa(@PathVariable Long id) {
        knowledgeService.deleteCurrentShopQa(id);
        return ResultVO.success();
    }
}
