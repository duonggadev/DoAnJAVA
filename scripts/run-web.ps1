#Requires -Version 5.1
param([switch]$ForceBuild)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$backendPort = 8098
$frontendPort = 5173
$frontendUrl = "http://127.0.0.1:$frontendPort"
$global:SoLopChildren = @()

function Get-Listeners([int]$port) {
    try {
        @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction Stop |
          Select-Object -ExpandProperty OwningProcess -Unique)
    } catch {
        @()
    }
}

# Không giết process của project khác: nếu cổng bận thì dừng và báo cho người dùng.
foreach ($port in @($backendPort, $frontendPort)) {
    $owners = @(Get-Listeners $port)
    if ($owners.Count -gt 0) {
        $info = ($owners | ForEach-Object {
            $proc = Get-Process -Id $_ -ErrorAction SilentlyContinue
            if ($proc) { "$($proc.ProcessName) (PID $_)" } else { "PID $_" }
        }) -join ', '
        throw "Cổng $port đang được dùng bởi $info. Hãy đóng ứng dụng đó trước. Script này không kill process của project khác."
    }
}

if (@(Get-Listeners 3306).Count -eq 0) {
    Write-Warning 'MySQL chưa nghe cổng 3306. Hãy chạy Laragon/MySQL trước khi đăng nhập.'
}

# Build backend nếu chưa có jar hoặc mã nguồn mới hơn jar.
$jar = Join-Path $root 'backend\target\so-lop.jar'
$needsBuild = $ForceBuild -or -not (Test-Path $jar)
if (-not $needsBuild) {
    $jarTime = (Get-Item $jar).LastWriteTime
    $newer = Get-ChildItem (Join-Path $root 'backend\src\main') -Recurse -File -ErrorAction SilentlyContinue |
             Where-Object { $_.LastWriteTime -gt $jarTime } | Select-Object -First 1
    if ($newer) { $needsBuild = $true }
}
if ($needsBuild) {
    Write-Host 'Dang build backend (lan dau hoac ma nguon moi)...'
    & mvn -f (Join-Path $root 'backend\pom.xml') package -DskipTests -q
    if ($LASTEXITCODE -ne 0) { throw 'Build backend that bai.' }
    if (-not (Test-Path $jar)) { throw "Khong tim thay file jar: $jar" }
}

$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
if (-not (Get-Command $java -ErrorAction SilentlyContinue)) { throw "Khong tim thay Java 21: $java" }

Write-Host "Khoi dong backend (MySQL) tai cong $backendPort ..."
$backend = Start-Process -FilePath $java -ArgumentList @('-jar', ('"' + $jar + '"'), "--server.port=$backendPort") `
    -WorkingDirectory (Join-Path $root 'backend') -PassThru -NoNewWindow

Write-Host "Khoi dong frontend tai cong $frontendPort ..."
$frontend = Start-Process -FilePath 'cmd.exe' -ArgumentList @('/c', 'npm.cmd run dev') `
    -WorkingDirectory (Join-Path $root 'frontend') -PassThru -NoNewWindow

$global:SoLopChildren = @($backend, $frontend)

# Dọn cả cây tiến trình (java / cmd / npm / node) khi PowerShell thoát bình thường.
Register-EngineEvent -SourceIdentifier PowerShell.Exiting -ErrorAction SilentlyContinue -Action {
    foreach ($p in $global:SoLopChildren) {
        try {
            if ($p -and -not $p.HasExited) { & taskkill /T /F /PID $p.Id 2>$null | Out-Null }
        } catch {}
    }
} | Out-Null

try {
    $ready = $false
    for ($i = 0; $i -lt 120; $i++) {
        if ($backend.HasExited -or $frontend.HasExited) {
            throw 'Mot tien trinh thoat som. Xem log trong cua so nay.'
        }
        try {
            $response = Invoke-WebRequest -Uri $frontendUrl -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
            if ($response.StatusCode -eq 200) { $ready = $true; break }
        } catch {}
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw 'Frontend khong san sang sau 60 giay.' }

    Write-Host ''
    Write-Host "San sang: $frontendUrl" -ForegroundColor Green
    Start-Process $frontendUrl
    Write-Host 'Nhan Enter de dung backend/frontend va giai phong cong 8098/5173 ...'
    Read-Host | Out-Null
}
finally {
    foreach ($p in $global:SoLopChildren) {
        if ($p -and -not $p.HasExited) {
            & taskkill /T /F /PID $p.Id 2>$null | Out-Null
        }
    }
    Start-Sleep -Milliseconds 700
    foreach ($port in @($backendPort, $frontendPort)) {
        if (@(Get-Listeners $port).Count -eq 0) {
            Write-Host "Da giai phong cong $port."
        } else {
            Write-Warning "Cong $port van con process; kiem tra lai bang netstat -ano | findstr :$port"
        }
    }
}
