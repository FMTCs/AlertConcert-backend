package fcmt.backend.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Component
public class AiClient {

	private final ChatClient chatClient;

	@Value("classpath:genre.json")
	private Resource genresResource;

	// 1. ChatClient.Builder를 주입받아 기본 시스템 설정을 입힙니다.
	public AiClient(ChatClient.Builder builder, @Value("${spring.ai.openai.chat.model}") String model,
			@Value("${spring.ai.openai.base-url}") String baseUrl) {
		log.info("🔥 AI model = {}", model);
		log.info("🔥 AI baseUrl = {}", baseUrl);
		this.chatClient = builder.defaultSystem("""
				당신은 한국에서 열리는 음악 콘서트를 대상으로 아티스트 데이터를 추출하는 AI 에이전트입니다.
				[절대 규칙]
				1. 반드시 JSON 리스트 포맷으로만 응답
				2. [1], [2] 와 같은 인용 표기 제외
				3. 밴드나 그룹은 단일 엔티티로 처리
				""")
			.defaultOptions(org.springframework.ai.openai.OpenAiChatOptions.builder().model(model).build())
			.build();
	}

	// 1단계: 아티스트 이름 리스트 추출
	public List<String> fetchArtistList(String concertName) {
		try {
			List<String> artists = chatClient.prompt()
				.user(u -> u.text("""
						'{concertName}' 공연의 출연 아티스트(가수/그룹) 이름을 JSON 리스트로 추출
						""").param("concertName", concertName))
				.call()
				.entity(new ParameterizedTypeReference<List<String>>() {
				});
			log.info("🔥 AI RAW RESPONSE = {}", artists);

			return artists != null ? artists : List.of();

		}
		catch (Exception e) {
			log.error("가수 리스트 추출 실패: {}", e.getMessage());
			return List.of();
		}
	}

	// 2단계: Spotify ID 추출 (Spotify API 사용)
	public List<ArtistIdRecord> fetchSpotifyIdByArtistName(String concertName, List<String> artistNames) {
		String artistListStr = IntStream.range(0, artistNames.size())
			.mapToObj(i -> String.format("%d. %s", i + 1, artistNames.get(i)))
			.collect(Collectors.joining("\n"));

		try {
			return chatClient.prompt()
				.user(u -> u.text("""
						[입력 데이터]
						콘서트명: {concertName}
						대상 아티스트:
						{artistListStr}

						[임무]
						위 아티스트들의 '공식 영문명(name)'과 'Spotify Artist ID(spotify_id)'를 찾아주세요.
						검색 결과가 없다면 빈 문자열("")을 반환하세요.
						""").param("concertName", concertName).param("artistListStr", artistListStr))
				.call()
				.entity(new ParameterizedTypeReference<List<ArtistIdRecord>>() {
				});
		}
		catch (Exception e) {
			log.error("Spotify ID 추출 실패: {}", e.getMessage());
			return List.of();
		}
	}

	// 3단계: 장르 분류 (genre.json 기반, full-path 1~3개 선택)
	public record ArtistGenreRecord(String spotify_artist_id, List<String> genres) {
		public ArtistGenreRecord {
			genres = (genres == null) ? List.of() : List.copyOf(genres);
		}
	}

	public ArtistGenreRecord fetchGenresWithAi(String spotifyArtistId, String artistName) {
		try {
			String allowedLeavesJson = genresResource.getContentAsString(StandardCharsets.UTF_8);

			List<ArtistGenreRecord> raw = chatClient.prompt()
				.user(u -> u.text("""
						너는 장르 분류기다.

						규칙:
						- 아래 genre.json 배열에 포함된 leaf 문자열 중에서만 선택
						- 1~3개만 선택
						- 응답은 JSON 리스트만
						- 리스트의 각 원소는 키 2개만 가진다: spotify_artist_id, genres
						- json 배열에 없는 값이면 genres는 빈 리스트로

						입력:
						artist_name: {artistName}
						spotify_artist_id: {spotifyArtistId}

						genre.json:
						{allowedLeavesJson}
						""")
					.param("artistName", artistName)
					.param("spotifyArtistId", spotifyArtistId)
					.param("allowedLeavesJson", allowedLeavesJson))
				.call()
				.entity(new ParameterizedTypeReference<List<ArtistGenreRecord>>() {
				});

			if (raw == null || raw.isEmpty() || raw.get(0) == null) {
				return new ArtistGenreRecord(spotifyArtistId, List.of());
			}

			List<String> cleaned = (raw.get(0).genres() == null ? List.<String>of() : raw.get(0).genres()).stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(s -> !s.isBlank())
				.distinct()
				.limit(3)
				.toList();

			return new ArtistGenreRecord(spotifyArtistId, cleaned);

		}
		catch (Exception e) {
			log.error("장르 분류 실패(artist={}): {}", artistName, e.getMessage(), e);
			return new ArtistGenreRecord(spotifyArtistId, List.of());
		}
	}

	public record ArtistIdRecord(String name, String spotify_id) {
	}

}