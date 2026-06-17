package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.UserShopBinding;
import com.gogoga.backend.vo.AdminPowerManagementRowVO;
import com.gogoga.backend.vo.UserShopBindingVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 后台用户门店绑定 Mapper
 */
@Mapper
public interface UserShopBindingMapper {

    int insert(UserShopBinding binding);

    UserShopBinding selectByUserIdAndPoiId(@Param("userId") String userId, @Param("poiId") String poiId);

    UserShopBinding selectByUserIdAndShopId(@Param("userId") String userId, @Param("shopId") Long shopId);

    UserShopBinding selectByPoiId(@Param("poiId") String poiId);

    UserShopBinding selectById(@Param("id") Long id);

    UserShopBinding selectByShopId(@Param("shopId") Long shopId);

    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") String userId);

    int deleteById(@Param("id") Long id);

    UserShopBinding selectByVisibleUserIdsAndShopId(
            @Param("visibleUserIds") List<String> visibleUserIds,
            @Param("shopId") Long shopId
    );

    List<UserShopBindingVO> selectBoundShopsByVisibleUserIds(
            @Param("visibleUserIds") List<String> visibleUserIds,
            @Param("targetUserId") String targetUserId,
            @Param("keyword") String keyword,
            @Param("promotionStatus") String promotionStatus,
            @Param("sortDirection") String sortDirection
    );

    default List<UserShopBindingVO> selectBoundShopsByVisibleUserIds(
            List<String> visibleUserIds,
            String targetUserId,
            String keyword,
            String sortDirection
    ) {
        return selectBoundShopsByVisibleUserIds(visibleUserIds, targetUserId, keyword, null, sortDirection);
    }

    List<UserShopBindingVO> selectBoundShopsByUserId(
            @Param("userId") String userId,
            @Param("keyword") String keyword,
            @Param("promotionStatus") String promotionStatus,
            @Param("sortDirection") String sortDirection
    );

    default List<UserShopBindingVO> selectBoundShopsByUserId(
            String userId,
            String keyword,
            String sortDirection
    ) {
        return selectBoundShopsByUserId(userId, keyword, null, sortDirection);
    }

    List<UserShopBindingVO> selectAllBoundShops(
            @Param("keyword") String keyword,
            @Param("promotionStatus") String promotionStatus,
            @Param("sortDirection") String sortDirection
    );

    default List<UserShopBindingVO> selectAllBoundShops(String keyword, String sortDirection) {
        return selectAllBoundShops(keyword, null, sortDirection);
    }

    List<UserShopBindingVO> selectAdminBoundShops(
            @Param("bindingDomain") String bindingDomain,
            @Param("targetUserId") String targetUserId,
            @Param("keyword") String keyword,
            @Param("promotionStatus") String promotionStatus,
            @Param("sortDirection") String sortDirection
    );

    default List<UserShopBindingVO> selectAdminBoundShops(
            String bindingDomain,
            String targetUserId,
            String keyword,
            String sortDirection
    ) {
        return selectAdminBoundShops(bindingDomain, targetUserId, keyword, null, sortDirection);
    }

    List<UserShopBindingVO> selectAdminMerchantShops(
            @Param("bindingDomain") String bindingDomain,
            @Param("bindingStatus") String bindingStatus,
            @Param("targetUserId") String targetUserId,
            @Param("keyword") String keyword,
            @Param("promotionStatus") String promotionStatus,
            @Param("sortDirection") String sortDirection
    );

    default List<UserShopBindingVO> selectAdminMerchantShops(
            String bindingDomain,
            String bindingStatus,
            String targetUserId,
            String keyword,
            String sortDirection
    ) {
        return selectAdminMerchantShops(bindingDomain, bindingStatus, targetUserId, keyword, null, sortDirection);
    }

    List<AdminPowerManagementRowVO> selectAllPowerManagedShops(
            @Param("targetUserId") String targetUserId,
            @Param("keyword") String keyword,
            @Param("riskStatus") String riskStatus,
            @Param("sortDirection") String sortDirection
    );

    List<AdminPowerManagementRowVO> selectAdminPowerManagedShops(
            @Param("bindingDomain") String bindingDomain,
            @Param("targetUserId") String targetUserId,
            @Param("keyword") String keyword,
            @Param("riskStatus") String riskStatus,
            @Param("sortDirection") String sortDirection
    );

    List<AdminPowerManagementRowVO> selectAdminPowerManagedMerchantShops(
            @Param("bindingDomain") String bindingDomain,
            @Param("bindingStatus") String bindingStatus,
            @Param("targetUserId") String targetUserId,
            @Param("keyword") String keyword,
            @Param("riskStatus") String riskStatus,
            @Param("sortDirection") String sortDirection
    );

    AdminPowerManagementRowVO selectPowerManagedShopByShopId(@Param("shopId") Long shopId);

    AdminPowerManagementRowVO selectPowerManagedMerchantByShopId(@Param("shopId") Long shopId);
}
