// User DB 부분 구현 필요
package fcmt.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "Users", uniqueConstraints = { @UniqueConstraint(columnNames = "id"),
		@UniqueConstraint(columnNames = "spotify_user_id") })
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long uid;

	@Column(length = 64, unique = true)
	private String id;

	@Column(name = "pw_hash", columnDefinition = "TEXT")
	private String password;

	@Column(name = "spotify_user_id", nullable = false, unique = true)
	private String spotifyUserId;

	/**
	 * 가입 생성 시각
	 */
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	/**
	 * 수정 시각 (trigger로 관리)
	 */
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	/**
	 * 회원가입 완료 여부
	 */
	@Builder.Default
	@Column(nullable = false)
	private boolean valid = false;

	/**
	 * 대칭키로 암호화된 Spotify refresh token
	 */
	@Column(name = "spotify_refresh_token_enc", columnDefinition = "TEXT")
	private String spotifyRefreshTokenEnc;

}