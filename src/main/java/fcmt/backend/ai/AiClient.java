package fcmt.backend.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

	private final ChatModel chatModel;

	// 1단계: 공연 정보를 바탕으로 아티스트 이름 리스트 추출
	// TODO: 포스터는 이미지가지고 넣어서 뭐 어찌저찌 하자고 했던거같은데.. 일단 그건 나중에..
	// TODO: Media 객체를 활용해 멀티모달 프롬프트를 구성해야..한다고 함
	public List<String> fetchArtistList(String concertName, String posterUrl) {
		ListOutputConverter outputConverter = new ListOutputConverter(new DefaultConversionService());
		String format = outputConverter.getFormat();

		String template = """
				콘서트명: {concertName}
				포스터 URL: {posterUrl}
				위 정보를 바탕으로 이 공연에 출연하는 주요 가수(가창자)들의 실제 이름을 리스트로 알려줘.
				{format}
				""";

		PromptTemplate promptTemplate = new PromptTemplate(template);
		Prompt prompt = promptTemplate
			.create(Map.of("concertName", concertName, "posterUrl", posterUrl, "format", format));

		try {
			String response = chatModel.call(prompt).getResult().getOutput().getText();
			return outputConverter.convert(response);
		}
		catch (Exception e) {
			log.error("가수 리스트 추출 실패: {}", e.getMessage());
			return List.of();
		}
	}

	// 2단계: 아티스트 이름을 기반으로 Spotify ID 추출
	public ArtistIdRecord fetchSpotifyIdByArtistName(String artistName) {
		BeanOutputConverter<ArtistIdRecord> outputConverter = new BeanOutputConverter<>(ArtistIdRecord.class);
		String format = outputConverter.getFormat();

		String template = """
				아티스트 '{artistName}'의 공식 Spotify Artist ID를 찾아줘.
				{format}
				""";

		PromptTemplate promptTemplate = new PromptTemplate(template);
		Prompt prompt = promptTemplate.create(Map.of("artistName", artistName, "format", format));

		try {
			String response = chatModel.call(prompt).getResult().getOutput().getText();
			return outputConverter.convert(response);
		}
		catch (Exception e) {
			log.error("Spotify ID 추출 실패 ({}): {}", artistName, e.getMessage());
			return null;
		}
	}

	// 결과 매핑을 위한 Record 정의
	public record ArtistIdRecord(String name, String spotify_id) {
	}

}