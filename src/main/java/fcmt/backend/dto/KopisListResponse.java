package fcmt.backend.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JacksonXmlRootElement(localName = "dbs")
public class KopisListResponse {

	@JacksonXmlProperty(localName = "db")
	@JacksonXmlElementWrapper(useWrapping = false)
	private List<KopisListDto> concertList;

	@Getter
	@Setter
	public static class KopisListDto {

		private String mt20id; // id만 뽑아서, KopisDetailResponse를 보기 위해서 사용.

	}

}