package com.cs.system.security;

import com.cs.framework.common.ErrorCode;
import com.cs.framework.common.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未认证兜底 —— 匿名（或 token 无效）访问受保护资源时返回
 * <b>HTTP 401</b> + 统一响应体（契约例外条款：未认证不走 HTTP 200 + code）。
 *
 * <p>过滤器链层的异常到不了 @RestControllerAdvice，必须独立实现。</p>
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                objectMapper.writeValueAsString(R.fail(ErrorCode.UNAUTHORIZED)));
    }
}
