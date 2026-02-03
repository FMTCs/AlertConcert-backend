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

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

	private final JwtTokenProvider jwtTokenProvider;

	private static final List<String> PERMIT_URLS = List.of("/auth/login", "/auth/register", "/auth/spotifyOAuth2",
			"/login/oauth2/code/spotify");

	private boolean isPermitUri(String uri) {
		return PERMIT_URLS.stream().anyMatch(uri::startsWith);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws IOException, ServletException {

		String requestUri = request.getRequestURI();

		System.out.println("현재 요청 URI: " + requestUri); // 이 로그가 핵심입니다!
		System.out.println("허용 여부: " + isPermitUri(requestUri));

		// 1. 인증이 필요 없는 white list의 경우
		if (isPermitUri(requestUri)) {
			filterChain.doFilter(request, response);
			return;
		}

		String authHeader = request.getHeader("Authorization");

		// 2. Header mismatch 시 unauthorized
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		// token parsing
		String token = authHeader.substring(7);

		try {
			// 3. Access token 검증
			Claims claims = jwtTokenProvider.validateAccessToken(token);

			// 4. ACCESS 토큰 아니면 권한 없음
			if (!jwtTokenProvider.isAccessToken(claims)) {
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				return;
			}

			// 5. claim에서 필요 정보 추출
			String userId = claims.getSubject(); // 로그인 ID
			// Long uid = claims.get("uid", Long.class);

			// 6. Authentication 생성
			UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userId, // 인증된
																													// 사용자
																													// 정보
					null, List.of());

			// 7. SecurityContext에 authentication 저장 -> 로그인 유지
			SecurityContextHolder.getContext().setAuthentication(authentication);

		}
		catch (Exception e) {
			// 토큰 문제 -> 그냥 인증 안 된 상태로 진행 -> test 용도
			SecurityContextHolder.clearContext();
			// [TODO] unAuthorize 연결 해야 함
			// response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			// return;
		}

		filterChain.doFilter(request, response);
	}

}
