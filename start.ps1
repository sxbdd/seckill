$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not (Test-Path 'target\seckill-1.0.0.jar')) {
    mvn -q -DskipTests package
}

$p = Start-Process -FilePath 'java' -ArgumentList @('-jar', 'target\seckill-1.0.0.jar') -WindowStyle Hidden -PassThru
$ok = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 1
    try {
        Invoke-WebRequest 'http://127.0.0.1:8080/actuator/health' -UseBasicParsing -TimeoutSec 2 | Out-Null
        $ok = $true
        break
    } catch {}
}
if ($ok) {
    Start-Process 'http://127.0.0.1:8080/'
    Write-Host '秒杀系统已启动，浏览器已打开 http://127.0.0.1:8080/'
} else {
    Write-Host '启动超时，请检查 8080 端口是否被占用'
    Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
}
