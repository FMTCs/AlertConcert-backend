package fcmt.backend.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Converter
@Component
@RequiredArgsConstructor
public class TokenEncryptionConverter implements AttributeConverter<String, String> {

	private final AesUtil aesUtil;

	@Override
	public String convertToDatabaseColumn(String attribute) {
		// 엔티티 -> DB 저장 시 암호화
		return aesUtil.encrypt(attribute);
	}

	@Override
	public String convertToEntityAttribute(String dbData) {
		// DB -> 엔티티 조회 시 복호화
		return aesUtil.decrypt(dbData);
	}

}
