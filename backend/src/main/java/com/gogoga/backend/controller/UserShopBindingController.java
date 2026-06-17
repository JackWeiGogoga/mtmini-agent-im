package com.gogoga.backend.controller;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.param.UserShopBatchBindParam;
import com.gogoga.backend.param.UserShopBindParam;
import com.gogoga.backend.service.UserShopBindingService;
import com.gogoga.backend.vo.ResultVO;
import com.gogoga.backend.vo.UserShopBatchBindResultVO;
import com.gogoga.backend.vo.UserShopBindingVO;
import com.gogoga.backend.vo.VisibleUserOptionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前登录用户门店绑定控制器
 */
@RestController
@RequestMapping("/api/system/user-shops")
@RequiredArgsConstructor
@RequiresLogin
public class UserShopBindingController {

    private final UserShopBindingService userShopBindingService;

    @GetMapping
    @RequiresPermission(value = "shop:binding:list,sales:binding:list,youzan:binding:list", logical = RequiresPermission.Logical.OR)
    public ResultVO<PageInfo<UserShopBindingVO>> listCurrentUserShops(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String targetUserId,
            @RequestParam(required = false) String bindingDomain,
            @RequestParam(required = false) String bindingStatus,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return ResultVO.success(userShopBindingService.listCurrentUserShops(keyword, targetUserId, bindingDomain, bindingStatus, sortField, sortOrder, pageNum, pageSize));
    }

    @GetMapping("/visible-users")
    @RequiresPermission(value = "shop:binding:list,sales:binding:list,youzan:binding:list", logical = RequiresPermission.Logical.OR)
    public ResultVO<List<VisibleUserOptionVO>> listVisibleUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String bindingDomain) {
        return ResultVO.success(userShopBindingService.listVisibleUsers(keyword, bindingDomain));
    }

    @PostMapping("/bind")
    @RequiresPermission(value = "shop:binding:add,sales:binding:add,youzan:binding:add", logical = RequiresPermission.Logical.OR)
    public ResultVO<UserShopBindingVO> bindCurrentUserShop(@Valid @RequestBody UserShopBindParam param) {
        return ResultVO.success(userShopBindingService.bindCurrentUserShop(param.getPoiId(), param.getTargetUserId(), param.getBindingDomain()));
    }

    @PostMapping("/bind/batch")
    @RequiresPermission(value = "shop:binding:add,sales:binding:add,youzan:binding:add", logical = RequiresPermission.Logical.OR)
    public ResultVO<UserShopBatchBindResultVO> batchBindCurrentUserShops(@Valid @RequestBody UserShopBatchBindParam param) {
        return ResultVO.success(userShopBindingService.batchBindCurrentUserShops(param.getPoiIds(), param.getTargetUserId(), param.getBindingDomain()));
    }

    @DeleteMapping("/{bindingId}")
    @RequiresPermission(value = "shop:binding:delete,sales:binding:delete,youzan:binding:delete", logical = RequiresPermission.Logical.OR)
    public ResultVO<Void> unbindCurrentUserShop(
            @PathVariable Long bindingId,
            @RequestParam(required = false) String bindingDomain) {
        userShopBindingService.unbindCurrentUserShop(bindingId, bindingDomain);
        return ResultVO.success();
    }
}
