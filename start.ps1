$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not (Test-Path 'target\seckill-1.0.0.jar')) {
    & mvn -q -DskipTests package
}

$proc = Start-Process -FilePath 'java' -ArgumentList @('-jar', 'target\seckill-1.0.0.jar') -WindowStyle Hidden -PassThru
$ready = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 1
    try {
        $resp = Invoke-WebRequest 'http://127.0.0.1:8080/actuator/health' -UseBasicParsing -TimeoutSec 2
        if ($resp.StatusCode -eq 200) { $ready = $true; break }
    } catch {}
}
if ($ready) {
    Start-Process 'http://127.0.0.1:8080/'
    Write-Host 'Seckill is running. Browser opened: http://127.0.0.1:8080/'
} else {
    Write-Host 'Startup timeout. Please check whether port 8080 is in use.'
    Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
}
