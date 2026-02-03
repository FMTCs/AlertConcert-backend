package fcmt.backend.controller;

import fcmt.backend.dto.SpotifyUserDto;
import fcmt.backend.security.JwtTokenProvider;
import fcmt.backend.service.SpotifyOAuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
public class SpotifyOAuthController {

	private final JwtTokenProvider jwtTokenProvider;

	private final SpotifyOAuthService spotifyOAuthService;

	public SpotifyOAuthController(JwtTokenProvider jwtTokenProvider, SpotifyOAuthService spotifyOAuthService) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.spotifyOAuthService = spotifyOAuthService;
	}

	@Value("${spotify.api.client-id}")
	private String clientId;

	@Value("${spotify.api.client-secret}")
	private String clientSecret;

	@Value("${spotify.api.redirect-uri}")
	private String redirectUri;

	// 사용자가 spotify 인증하기 버튼을 누르면 redirect 주소를 생성해서 넘겨줌
	@GetMapping("/auth/spotifyOAuth2")
	public void redirectToSpotify(HttpServletResponse response) throws IOException {
		String spotifyOAuthUrl = "https://accounts.spotify.com/authorize" + "?client_id=" + clientId
				+ "&response_type=code" + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
				+ "&scope=" + URLEncoder.encode("user-read-private user-read-email", StandardCharsets.UTF_8);
		System.out.println("test중 : " + spotifyOAuthUrl);
		response.sendRedirect(spotifyOAuthUrl);
	}

	// spotifyOAuth2 요청을 보내고 spotify api에서 redirect로 api callback처리 컨트롤러
	@GetMapping("/login/oauth2/code/spotify")
	public void spotifyCallback(@RequestParam("code") String code, HttpServletResponse response) throws IOException {
		// 1. Authorization Code로 Access Token 요청
		String accessToken = spotifyOAuthService.getAccessToken(code);

		// 2. Access Token으로 사용자 정보 요청 (/v1/me api call)
		SpotifyUserDto userInfo = spotifyOAuthService.getSpotifyUserInfo(accessToken);

		// 3. [ToDo] DB 확인 후 회원가입 또는 로그인 처리

		// 4. 우리 서비스 전용 JWT 토큰 생성 및 반환
		String registerToken = jwtTokenProvider.createRegisterToken(userInfo.getEmail());

		// 5. [ToDo] JWT 토큰을 Redis에 저장 -> register 시 토큰 확인용

		// 6. 프론트엔드로 리다이렉트 (프론트 주소가 localhost:3000인 경우)
		// 보안상 중요 데이터는 쿠키나 쿼리 파라미터(짧은 유효기간)로 전달합니다.
		String targetUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/spotify-callback")
			.queryParam("token", registerToken)
			.queryParam("id", userInfo.getId())
			.build()
			.toUriString();

		response.sendRedirect(targetUrl);
	}

}
