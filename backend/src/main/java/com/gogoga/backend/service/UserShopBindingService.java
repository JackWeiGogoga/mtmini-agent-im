package com.gogoga.backend.service;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.vo.UserShopBatchBindResultVO;
import com.gogoga.backend.vo.UserShopBindingVO;
import com.gogoga.backend.vo.VisibleUserOptionVO;

import java.util.List;

/**
 * 当前登录用户门店绑定服务
 */
public interface UserShopBindingService {

    UserShopBindingVO bindCurrentUserShop(String poiId, String targetUserId);

    UserShopBindingVO bindCurrentUserShop(String poiId, String targetUserId, String bindingDomain);

    UserShopBatchBindResultVO batchBindCurrentUserShops(List<String> poiIds, String targetUserId);

    UserShopBatchBindResultVO batchBindCurrentUserShops(List<String> poiIds, String targetUserId, String bindingDomain);

    PageInfo<UserShopBindingVO> listCurrentUserShops(String keyword, String targetUserId, Integer pageNum, Integer pageSize);

    PageInfo<UserShopBindingVO> listCurrentUserShops(String keyword, String targetUserId, String bindingDomain, Integer pageNum, Integer pageSize);

    PageInfo<UserShopBindingVO> listCurrentUserShops(String keyword, String targetUserId, String bindingDomain, String bindingStatus, Integer pageNum, Integer pageSize);

    PageInfo<UserShopBindingVO> listCurrentUserShops(String keyword, String targetUserId, String bindingDomain, String bindingStatus, String sortField, String sortOrder, Integer pageNum, Integer pageSize);

    PageInfo<UserShopBindingVO> listCurrentUserShops(String keyword, String targetUserId, String bindingDomain, String bindingStatus, String promotionStatus, String sortField, String sortOrder, Integer pageNum, Integer pageSize);

    List<VisibleUserOptionVO> listVisibleUsers(String keyword);

    List<VisibleUserOptionVO> listVisibleUsers(String keyword, String bindingDomain);

    MtShop requireCurrentUserBoundShop(Long shopId);

    MtShop requireCurrentUserBoundShop(Long shopId, String bindingDomain);

    MtShop requireCurrentUserVisibleShop(Long shopId, String bindingDomain);

    void unbindCurrentUserShop(Long bindingId);

    void unbindCurrentUserShop(Long bindingId, String bindingDomain);
}
