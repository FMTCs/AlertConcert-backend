echo "Installing Git hooks..."

# hooks 디렉토리에서 .git/hooks로 복사
cp hooks/* .git/hooks/

# 실행 권한 부여
chmod +x .git/hooks/*

echo "✅ Git hooks installed successfully!"