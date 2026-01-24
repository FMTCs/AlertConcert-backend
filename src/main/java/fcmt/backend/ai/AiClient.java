package fcmt.backend.ai;

import fcmt.backend.dto.ArtistAiResponseDto;
import fcmt.backend.dto.ArtistListWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Component
public class AiClient {

	private final ChatClient chatClient;

	@Value("classpath:genres.json")
	private Resource genresResource;

	// 1. ChatClient.Builder를 주입받아 기본 시스템 설정을 입힙니다.
	public AiClient(ChatClient.Builder builder,
					@Value("${spring.ai.openai.chat.model}")
					String model,
	@Value("${spring.ai.openai.base-url}")
	String baseUrl) {
		log.info("🔥 AI model = {}", model);
		log.info("🔥 AI baseUrl = {}", baseUrl);
		this.chatClient = builder.defaultSystem("""
				당신은 한국에서 열리는 음악 콘서트를 대상으로 아티스트 데이터를 추출하는 AI 에이전트입니다.
				[절대 규칙]
				1. 출력 형식: 반드시 지정된 형식으로만 응답하세요.
				2. 인용 금지: [1], [2] 와 같은 표기를 포함하지 마세요.
				3. 그룹 처리: 밴드나 그룹은 단일 엔티티로 처리하세요.
				4. 환각 방지: 확실하지 않으면 빈 값을 반환하세요.
				""").defaultOptions(org.springframework.ai.openai.OpenAiChatOptions.builder()
				.model("sonar")
				.build()).build();
	}

	// 1단계: 아티스트 이름 리스트 추출
	public List<String> fetchArtistList(String concertName, String posterUrl) {
		try {
			var response = chatClient.prompt()
				.user(u -> u.text("""
						[입력 데이터]
						콘서트명: {concertName}
						포스터 URL: {posterUrl}

						[임무]
						입력된 정보에서 공연의 '메인 아티스트(가수/그룹)' 이름을 리스트로 추출하세요.
						""").param("concertName", concertName).param("posterUrl", posterUrl))
				.call();
			String raw = response.content();
			log.info("🔥 AI RAW RESPONSE = {}", raw);

			// 2.0의 .entity()는 마크다운 제거 및 JSON 파싱을 자동으로 수행합니다.
			return response.entity(new ParameterizedTypeReference<List<String>>() {
			});
		}
		catch (Exception e) {
			log.error("가수 리스트 추출 실패: {}", e.getMessage());
			return List.of();
		}
	}

	// 2단계: Spotify ID 추출
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

	// 3단계: 장르 분류
	public List<ArtistAiResponseDto> fetchGenresFromAi(List<Map<String, String>> artists) {
		try {
			String allowedGenres = genresResource.getContentAsString(StandardCharsets.UTF_8);
			String artistListStr = artists.stream()
				.map(m -> String.format("- Name: %s (ID: %s)", m.get("artist_name"), m.get("spotify_artist_id")))
				.collect(Collectors.joining("\n"));

			return chatClient.prompt()
				.user(u -> u.text("""
						You are a music genre classifier.
						Return genres ONLY from the allowed list.

						Artists:
						{artistListStr}

						Allowed Genres:
						{allowedGenres}
						""").param("artistListStr", artistListStr).param("allowedGenres", allowedGenres))
				.call()
				.entity(ArtistListWrapper.class)
				.artists(); // Wrapper에서 리스트 추출
		}
		catch (Exception e) {
			log.error("AI 장르 추출 중 오류 발생: ", e);
			return List.of();
		}
	}

	public record ArtistIdRecord(String name, String spotify_id) {
	}

}