package fcmt.backend.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AesUtil {

	@Value("${encryption.secret}")
	private String secretKey;

	private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

	private static final int IV_SIZE = 16;

	private final SecureRandom secureRandom = new SecureRandom();

	public String encrypt(String plainText) {
		if (plainText == null)
			return null;
		try {
			byte[] iv = new byte[IV_SIZE];
			secureRandom.nextBytes(iv);
			IvParameterSpec ivSpec = new IvParameterSpec(iv);
			SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");

			Cipher cipher = Cipher.getInstance(ALGORITHM);
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
			byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

			byte[] combined = new byte[iv.length + encrypted.length];
			System.arraycopy(iv, 0, combined, 0, iv.length);
			System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

			return Base64.getEncoder().encodeToString(combined);
		}
		catch (Exception e) {
			throw new RuntimeException("Encryption failed", e);
		}
	}

	public String decrypt(String cipherText) {
		if (cipherText == null)
			return null;
		try {
			byte[] combined = Base64.getDecoder().decode(cipherText);
			byte[] iv = new byte[IV_SIZE];
			System.arraycopy(combined, 0, iv, 0, iv.length);

			byte[] encrypted = new byte[combined.length - IV_SIZE];
			System.arraycopy(combined, IV_SIZE, encrypted, 0, encrypted.length);

			IvParameterSpec ivSpec = new IvParameterSpec(iv);
			SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");

			Cipher cipher = Cipher.getInstance(ALGORITHM);
			cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

			return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
		}
		catch (Exception e) {
			throw new RuntimeException("Decryption failed", e);
		}
	}

}
