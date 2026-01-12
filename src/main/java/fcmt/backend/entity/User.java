// User DB 부분 구현 필요
package fcmt.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long uid;

	@Column(name = "username", length = 64, unique = true)
	private String username;

	@Column(name = "pw_hash", columnDefinition = "TEXT")
	private String password;

	@Column(name = "spotify_user_id", nullable = false, unique = true)
	private String spotifyUserId;

	@CreatedDate // 생성 시 자동 저장
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Builder.Default
	@Column(nullable = false)
	private boolean valid = false;

	/**
	 * 대칭키로 암호화된 Spotify refresh token
	 */
	@Column(name = "spotify_refresh_token_enc", columnDefinition = "TEXT")
	private String spotifyRefreshTokenEnc;

}