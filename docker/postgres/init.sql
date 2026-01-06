-- 부족/미정 정보는 TODO로 표시

BEGIN;

-- =========================
-- Common trigger: updated_at
-- =========================
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =========================
-- 1) Users
-- =========================
CREATE TABLE IF NOT EXISTS users (
  uid BIGSERIAL PRIMARY KEY,

  -- NOTE: spotify 인증 먼저 -> users row를 "임시 생성(valid=false)" 할 수 있게 nullable 허용
  id VARCHAR(64) UNIQUE, -- TODO(정책): 가입 완료 후에만 필수
  pw_hash TEXT, -- TODO(정책): 가입 완료 후에만 필수 (bcrypt 해시 문자열)

  spotify_user_id TEXT UNIQUE, -- TODO(정책): spotify 기반 서비스면 NOT NULL 권장

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- valid = signup completed
  valid BOOLEAN NOT NULL DEFAULT FALSE,

  -- refresh_token은 bcrypt로 저장하면 "복호화 불가"라 사용 불가
  -- 대칭키 암호화(AES-GCM 등) 결과를 저장하는 용도 (base64면 TEXT, 바이너리면 BYTEA)
  spotify_refresh_token_enc TEXT -- TODO(형식): base64 문자열로 저장 가정. 바이너리면 BYTEA로 변경
);

COMMENT ON COLUMN users.valid IS '회원가입 완료 여부: spotify 인증 직후 임시 생성은 false, 가입 완료 후 true';
COMMENT ON COLUMN users.spotify_refresh_token_enc IS '대칭키로 암호화된 refresh token (bcrypt X)';

-- 가입 완료(valid=true) 상태에서는 id/pw_hash가 반드시 있어야 한다
ALTER TABLE users
  ADD CONSTRAINT chk_users_required_when_valid
  CHECK (valid = FALSE OR (id IS NOT NULL AND pw_hash IS NOT NULL));

-- updated_at 트리거
DROP TRIGGER IF EXISTS trg_users_updated_at ON users;
CREATE TRIGGER trg_users_updated_at
  BEFORE UPDATE ON users
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

CREATE INDEX IF NOT EXISTS idx_users_created_at ON users(created_at);

-- =========================
-- 2) User Preferences (유저 선호도)
-- =========================
CREATE TABLE IF NOT EXISTS user_preferences (
  uid BIGINT PRIMARY KEY REFERENCES users(uid) ON DELETE CASCADE,
  preference JSONB NOT NULL DEFAULT '{}'::jsonb, -- genre & artist JSON
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_user_preferences_updated_at ON user_preferences;
CREATE TRIGGER trg_user_preferences_updated_at
  BEFORE UPDATE ON user_preferences
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

CREATE INDEX IF NOT EXISTS gin_user_preferences_preference
  ON user_preferences USING GIN (preference);

-- =========================
-- 3) Concerts
-- =========================
CREATE TABLE IF NOT EXISTS concerts (
  concert_id BIGSERIAL PRIMARY KEY,
  concert_name TEXT NOT NULL,

  -- 장르 복수 선택
  genres TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],

  -- TODO(casts): 아직 데이터 구조 미정 -> JSONB로 임시 유지(향후 정규화 가능)
  casts JSONB,

  -- TODO(예매시간): 현재는 "일" 단위만 저장. 시간 필요해지면 TIMESTAMPTZ로 변경
  booking_start_date DATE,
  booking_end_date DATE,

  booking_url TEXT, -- TODO(검증): URL 형식 체크 필요 여부
  poster_img_url TEXT, -- TODO(검증): URL 형식 체크 필요 여부

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT chk_booking_date_range
  CHECK (booking_start_date IS NULL OR booking_end_date IS NULL OR booking_end_date >= booking_start_date)
);

DROP TRIGGER IF EXISTS trg_concerts_updated_at ON concerts;
CREATE TRIGGER trg_concerts_updated_at
  BEFORE UPDATE ON concerts
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

-- 장르 포함 검색(예: WHERE genres @> ARRAY['rock']::text[])
CREATE INDEX IF NOT EXISTS gin_concerts_genres ON concerts USING GIN (genres);
CREATE INDEX IF NOT EXISTS idx_concerts_booking_dates ON concerts(booking_start_date, booking_end_date);

COMMIT;
