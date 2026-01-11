package fcmt.backend.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

	private final JwtTokenProvider jwtTokenProvider;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws IOException, ServletException {
		String authHeader = request.getHeader("Authorization");

		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}

		String token = authHeader.substring(7);

		try {
			if (!jwtTokenProvider.validateToken(token)) {
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				return;
			}

			Claims claims = jwtTokenProvider.parseClaims(token);

			// ACCESS 토큰만 허용
			if (jwtTokenProvider.isAccessToken(claims)) {
				String userId = claims.getSubject(); // 로그인 ID
				Long uid = claims.get("uid", Long.class);

				UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userId,
						null, List.of() // 권한은 나중에 cnrk - Role 등등
				);

				SecurityContextHolder.getContext().setAuthentication(authentication);
			}
		}
		catch (Exception e) {
			// 토큰 문제 -> 그냥 인증 안 된 상태로 진행 -> test 용도
			SecurityContextHolder.clearContext();
			// unAuthorize 연결 해야 함
		}

		filterChain.doFilter(request, response);
	}

}
