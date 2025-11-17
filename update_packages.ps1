# Script cập nhật package declarations và imports

# 1. Cập nhật package declarations
Get-ChildItem "src\server\core\*.java" | ForEach-Object { (Get-Content $_.FullName) -replace "^package server;", "package server.core;" | Set-Content $_.FullName }
Get-ChildItem "src\server\handlers\*.java" | ForEach-Object { (Get-Content $_.FullName) -replace "^package server;", "package server.handlers;" | Set-Content $_.FullName }
Get-ChildItem "src\server\managers\*.java" | ForEach-Object { (Get-Content $_.FullName) -replace "^package server;", "package server.managers;" | Set-Content $_.FullName }
Get-ChildItem "src\server\database\*.java" | ForEach-Object { (Get-Content $_.FullName) -replace "^package server;", "package server.database;" | Set-Content $_.FullName }
Get-ChildItem "src\server\game\*.java" | ForEach-Object { (Get-Content $_.FullName) -replace "^package server;", "package server.game;" | Set-Content $_.FullName }
Get-ChildItem "src\server\models\*.java" | ForEach-Object { (Get-Content $_.FullName) -replace "^package server;", "package server.models;" | Set-Content $_.FullName }

Write-Host "✅ Đã cập nhật package declarations"

# 2. Thêm imports vào tất cả files
$allFiles = Get-ChildItem "src\server" -Recurse -Filter "*.java"

foreach ($file in $allFiles) {
    $content = Get-Content $file.FullName -Raw
    
    # Nếu chưa có import server.*, thêm vào sau package declaration
    if ($content -notmatch "import server\.") {
        $content = $content -replace "(package server\.[^;]+;)", "`$1`n`nimport server.core.*;`nimport server.handlers.*;`nimport server.managers.*;`nimport server.database.*;`nimport server.game.*;`nimport server.models.*;"
        Set-Content -Path $file.FullName -Value $content -NoNewline
    }
}

Write-Host "✅ Đã thêm imports"
Write-Host "🎉 Hoàn thành!"
