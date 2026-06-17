package com.gogoga.backend.interceptor;

import com.gogoga.backend.annotation.RequiresShopRight;
import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.service.MtShopRightService;
import com.gogoga.backend.util.JsonUtil;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.ResultVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 门店权益拦截器
 *
 * @author system
 * @since 2026-03-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopRightInterceptor implements HandlerInterceptor {

    private final MtShopRightService mtShopRightService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;

        RequiresShopRight requiresShopRight = getRequiresShopRight(handlerMethod);
        if (requiresShopRight == null) {
            return true;
        }

        LoginUser loginUser = UserContext.getLoginUser();
        if (loginUser == null) {
            writeForbiddenResponse(response, "请先登录");
            return false;
        }

        boolean hasRight = mtShopRightService.hasCurrentRight(requiresShopRight.logical(), requiresShopRight.value());
        if (!hasRight) {
            log.warn("门店 {} 无权访问 {}，需要权益: {}", loginUser.getEpoiId(), request.getRequestURI(), String.join(",", requiresShopRight.value()));
            writeForbiddenResponse(response, "当前门店未开通所需权益");
            return false;
        }
        return true;
    }

    private RequiresShopRight getRequiresShopRight(HandlerMethod handlerMethod) {
        RequiresShopRight annotation = handlerMethod.getMethodAnnotation(RequiresShopRight.class);
        if (annotation != null) {
            return annotation;
        }
        return handlerMethod.getBeanType().getAnnotation(RequiresShopRight.class);
    }

    private void writeForbiddenResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JsonUtil.toJson(ResultVO.error(HttpStatus.FORBIDDEN.value(), message)));
    }
}
