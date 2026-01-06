-- 초기 데이터베이스 스키마 예시
CREATE TABLE IF NOT EXISTS users (
                                     id SERIAL PRIMARY KEY,
                                     username VARCHAR(50) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- 초기 데이터 예시
INSERT INTO users (username, email) VALUES ('admin', 'admin@example.com');