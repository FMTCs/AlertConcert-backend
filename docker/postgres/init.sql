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
  username VARCHAR(64) UNIQUE,
  pw_hash TEXT,

  spotify_user_id TEXT UNIQUE NOT NULL,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- valid = signup completed
  valid BOOLEAN NOT NULL DEFAULT FALSE,

  -- refresh_token은 bcrypt로 저장하면 "복호화 불가"라 사용 불가
  spotify_refresh_token_enc TEXT
);

COMMENT ON COLUMN users.valid IS '회원가입 완료 여부: spotify 인증 직후 임시 생성은 false, 가입 완료 후 true';
COMMENT ON COLUMN users.spotify_refresh_token_enc IS '대칭키로 암호화된 refresh token (bcrypt X)';

-- 가입 완료(valid=true) 상태에서는 id/pw_hash가 반드시 있어야 한다
ALTER TABLE users
  ADD CONSTRAINT chk_users_required_when_valid
  CHECK (valid = FALSE OR (username IS NOT NULL AND pw_hash IS NOT NULL));

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
  artist_ids BIGINT[] NOT NULL DEFAULT ARRAY[]::BIGINT[],
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_user_preferences_updated_at ON user_preferences;
CREATE TRIGGER trg_user_preferences_updated_at
  BEFORE UPDATE ON user_preferences
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

CREATE INDEX IF NOT EXISTS gin_user_preferences_preference
    ON user_preferences USING GIN (artist_ids);

-- =========================
-- 3) Concerts
-- =========================
CREATE TABLE IF NOT EXISTS concerts (
  concert_id BIGSERIAL PRIMARY KEY,
  concert_name TEXT NOT NULL,
  casts BIGINT[] NOT NULL DEFAULT ARRAY[]::BIGINT[],

  performance_start_date DATE,
  performance_end_date DATE,

  booking_url TEXT,
  poster_img_url TEXT,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()

);

DROP TRIGGER IF EXISTS trg_concerts_updated_at ON concerts;
CREATE TRIGGER trg_concerts_updated_at
  BEFORE UPDATE ON concerts
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at();

CREATE INDEX IF NOT EXISTS gin_concerts_casts ON concerts USING GIN (casts);

-- =========================
-- 4) Artists
-- =========================
CREATE TABLE IF NOT EXISTS artists (
  artist_id BIGSERIAL PRIMARY KEY,
  spotify_artist_id TEXT UNIQUE NOT NULL,
  genres TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
  artist_name TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS gin_artists_genres ON artists USING GIN (genres);
COMMIT;
