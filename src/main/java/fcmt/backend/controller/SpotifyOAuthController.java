package fcmt.backend.controller;

import fcmt.backend.dto.SpotifyTokenResponseDto;
import fcmt.backend.dto.SpotifyUserDto;
import fcmt.backend.repository.SignupTokenRepository;
import fcmt.backend.security.JwtTokenProvider;
import fcmt.backend.service.SpotifyOAuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
public class SpotifyOAuthController {

	private final JwtTokenProvider jwtTokenProvider;

	private final SignupTokenRepository signupTokenRepository;

	private final SpotifyOAuthService spotifyOAuthService;

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
				+ "&scope=" + URLEncoder.encode("user-read-private user-read-email", StandardCharsets.UTF_8)
				+ "&show_dialog=true";

		// spotify 인증 주소로 redirect
		response.sendRedirect(spotifyOAuthUrl);
	}

	// spotifyOAuth2 요청을 보내고 spotify api에서 redirect로 api callback처리 컨트롤러
	@GetMapping("/login/oauth2/code/spotify")
	public void spotifyCallback(@RequestParam("code") String code, HttpServletResponse response) throws IOException {
		// 1. Authorization Code로 Access Token 요청
		SpotifyTokenResponseDto spotifyTokens = spotifyOAuthService.getTokens(code);
		String spotifyAccessToken = spotifyTokens.getAccessToken();
		String spotifyRefreshToken = spotifyTokens.getRefreshToken();

		// 2. Access Token으로 사용자 정보 요청 (/v1/me api call)
		SpotifyUserDto userInfo = spotifyOAuthService.getSpotifyUserInfo(spotifyAccessToken);
		String spotifyUserId = userInfo.getId();

		// 3. 우리 서비스 전용 JWT 토큰 생성 및 반환
		String signupToken = jwtTokenProvider.createRegisterToken(spotifyUserId);

		// 4. Redis에 인증 관련 정보 및 확인용 토큰 저장
		signupTokenRepository.save(signupToken, spotifyUserId, spotifyRefreshToken);

		// 5. 프론트엔드로 리다이렉트
		String targetUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/signup")
			.queryParam("signupToken", signupToken)
			.build()
			.toUriString();

		response.sendRedirect(targetUrl);
	}

}
