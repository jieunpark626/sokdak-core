package com.sokdak.config

import com.sokdak.common.constants.HttpHeaders
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Gateway에서 검증된 사용자 정보를 받아서 SecurityContext에 설정하는 필터
 *
 * Gateway는 JWT 검증 후 X-User-Id 헤더에 사용자 ID를 전달합니다.
 * 보안을 위해 X-Gateway-Token 헤더로 Gateway의 요청임을 검증합니다.
 */
@Component
class GatewayAuthenticationFilter(
    @Value("\${gateway.security.token:}") private val gatewaySecurityToken: String,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return path == "/error" || path.startsWith("/actuator")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            val path = request.requestURI
            val isPublicPath = path.startsWith("/auth/")

            // 1. Gateway 토큰 검증 (모든 요청에서 필수)
            val gatewayToken = request.getHeader(HttpHeaders.GATEWAY_TOKEN)

            if (gatewaySecurityToken.isNotBlank() && gatewayToken != gatewaySecurityToken) {
                log.warn("Invalid or missing gateway token from IP: ${request.remoteAddr}, path: $path")
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Direct access not allowed")
                return
            }

            // 2. Gateway에서 전달한 사용자 ID 추출
            val userId = request.getHeader(HttpHeaders.USER_ID)

            // 3. 공개 경로가 아닌 경우 User ID 필수
            if (!isPublicPath && userId.isNullOrBlank()) {
                log.debug("No user ID found in request headers for protected path: $path")
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required")
                return
            }

            // 4. User ID가 있으면 SecurityContext에 인증 정보 설정
            if (!userId.isNullOrBlank()) {
                val authentication =
                    UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        emptyList(),
                    )
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)

                SecurityContextHolder.getContext().authentication = authentication

                log.debug("Authenticated user: $userId via Gateway")
            } else {
                log.debug("Public path access: $path")
            }
        } catch (e: Exception) {
            log.error("Error during gateway authentication", e)
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Authentication error")
            return
        }

        filterChain.doFilter(request, response)
    }
}
