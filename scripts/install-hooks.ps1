Write-Host "Installing Git hooks..." -ForegroundColor Cyan

# 1. 대상 디렉토리(.git/hooks) 존재 여부 확인
$targetDir = ".git/hooks"
if (-not (Test-Path $targetDir)) {
    Write-Error "Error: .git directory not found. Are you in the root of your repo?"
    exit 1
}

# 2. hooks 디렉토리의 파일을 .git/hooks로 복사
# -Force: 대상 파일이 있으면 덮어쓰기
Copy-Item -Path "hooks\*" -Destination $targetDir -Force

# 3. 성공 메시지 출력
Write-Host "Git hooks installed successfully!" -ForegroundColor Green