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
public class KopisDetailResponse {

	@JacksonXmlProperty(localName = "db")
	private KopisDetailDto detail;

	@Getter
	@Setter
	public static class KopisDetailDto {

		private String prfnm; // 공연명

		private String prfpdfrom; // 공연시작일

		private String prfpdto; // 공연종료일

		private String poster; // 포스터URL

		@JacksonXmlProperty(localName = "relates")
		private Relates relates; // relatenm 예매처명1 / relateurl 예매처URL1

	}

	@Getter
	@Setter
	public static class Relates {

		@JacksonXmlProperty(localName = "relate")
		@JacksonXmlElementWrapper(useWrapping = false)
		private List<Relate> relateList;

		public String getFirstUrl() {
			if (relateList != null && !relateList.isEmpty()) {
				return relateList.get(0).getRelateurl();
			}
			return null;
		}

	}

	@Getter
	@Setter
	public static class Relate {

		private String relatename;

		private String relateurl;

	}

}