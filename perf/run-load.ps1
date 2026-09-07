param(
  [string]$Mode = 'B',
  [int]$Threads = 300,
  [int]$Stock = 300
)
$ErrorActionPreference = 'Continue'
$root = Split-Path $PSScriptRoot -Parent
$perf = $PSScriptRoot
$mysql = 'D:\Dev\Env\mysql-8.0.46\bin\mysql.exe'
$redis = 'D:\Dev\Env\Redis-8.6.5\redis-cli.exe'
$jmeterBat = 'D:\Dev\Tools\apache-jmeter-5.6.3\bin\jmeter.bat'
$jar = Join-Path $root 'target\seckill-1.0.0.jar'
$port = 8080

Set-Location $root
if (-not (Test-Path $jar)) {
  & mvn -q -DskipTests package
  if ($LASTEXITCODE -ne 0) { throw 'build failed' }
}

Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*seckill*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
Start-Sleep -Seconds 1

$cleanup = "DELETE o FROM t_seckill_order o JOIN t_user u ON u.id=o.user_id WHERE u.username LIKE 'ltuser%' OR u.username REGEXP '^lt[0-9]+$'; DELETE FROM t_user WHERE username LIKE 'ltuser%' OR username REGEXP '^lt[0-9]+$';"
& $mysql -uroot -proot --host=127.0.0.1 --port=3306 seckill -e $cleanup 2>$null

$batch = New-Object System.Collections.Generic.List[string]
for ($i = 0; $i -lt $Threads; $i++) {
  $batch.Add("('ltuser$i','x','load$i','USER',1)")
  if ($batch.Count -ge 200 -or $i -eq ($Threads - 1)) {
    $sql = "INSERT INTO t_user (username,password,nickname,role,status) VALUES " + ($batch -join ',') + ";"
    & $mysql -uroot -proot --host=127.0.0.1 --port=3306 seckill -e $sql 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'insert users failed' }
    $batch.Clear()
  }
}

$ids = & $mysql -N -uroot -proot --host=127.0.0.1 --port=3306 seckill -e "SELECT id FROM t_user WHERE username LIKE 'ltuser%' ORDER BY id;" 2>$null
$csvPath = Join-Path $perf 'tokens.csv'
$lines = New-Object System.Collections.Generic.List[string]
foreach ($id in $ids) {
  if ([string]::IsNullOrWhiteSpace($id)) { continue }
  $tok = [guid]::NewGuid().ToString('N')
  & $redis -h 127.0.0.1 -p 6379 SET "user:token:$tok" "$id|USER" EX 7200 | Out-Null
  $lines.Add($tok)
}
Set-Content -LiteralPath $csvPath -Value ($lines -join "`r`n") -Encoding ASCII

$actOut = & $mysql -N -uroot -proot --host=127.0.0.1 --port=3306 seckill -e "INSERT INTO t_seckill_activity (goods_id,seckill_price,start_time,end_time,status) VALUES (1,99.00,DATE_SUB(NOW(), INTERVAL 1 HOUR),DATE_ADD(NOW(), INTERVAL 2 HOUR),1); SELECT LAST_INSERT_ID();" 2>$null
$aid = ($actOut | Select-Object -Last 1)
& $mysql -uroot -proot --host=127.0.0.1 --port=3306 seckill -e "INSERT INTO t_seckill_stock (activity_id,stock) VALUES ($aid,$Stock);" 2>$null

$jmx = Get-Content -LiteralPath (Join-Path $perf 'seckill-burst.jmx.template') -Raw
$jmx = $jmx.Replace('__THREADS__', [string]$Threads)
$jmx = $jmx.Replace('__CSV__', $csvPath.Replace('\', '/'))
$jmx = $jmx.Replace('__PORT__', [string]$port)
$jmx = $jmx.Replace('__AID__', [string]$aid)
$planPath = Join-Path $perf 'seckill-burst.jmx'
Set-Content -LiteralPath $planPath -Value $jmx -Encoding ASCII

$redisEnabled = 'true'
if ($Mode -eq 'A') { $redisEnabled = 'false' }
$proc = Start-Process -FilePath 'java' -ArgumentList @('-jar', $jar, "--server.port=$port", "--seckill.redis-enabled=$redisEnabled", "--server.tomcat.threads.max=500", "--server.tomcat.accept-count=1000", "--spring.datasource.hikari.maximum-pool-size=50") -WindowStyle Hidden -PassThru
$ok = $false
for ($i = 0; $i -lt 60; $i++) {
  Start-Sleep -Seconds 1
  try { $r = Invoke-WebRequest "http://127.0.0.1:$port/actuator/health" -UseBasicParsing -TimeoutSec 2; if ($r.StatusCode -eq 200) { $ok = $true; break } } catch {}
}
if (-not $ok) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue; throw 'app start timeout' }

$jtl = Join-Path $perf "result-$Mode.jtl"
$html = Join-Path $perf "report-$Mode"
if (Test-Path $jtl) { Remove-Item $jtl -Force }
if (Test-Path $html) { Remove-Item $html -Recurse -Force }
& $jmeterBat -n -t $planPath -l $jtl -e -o $html | Out-Null
$jmExit = $LASTEXITCODE
Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue

$total = 0; $err = 0; $minT = [long]::MaxValue; $maxT = [long]0
if (Test-Path $jtl) {
  $samples = Get-Content $jtl | Select-Object -Skip 1
  foreach ($s in $samples) {
    if ([string]::IsNullOrWhiteSpace($s)) { continue }
    $c = $s.Split(',')
    if ($c.Length -lt 8) { continue }
    $total++
    $ts = [long]$c[0]; $el = [long]$c[1]; $success = $c[7]
    if ($success -ne 'true') { $err++ }
    $end = $ts + $el
    if ($ts -lt $minT) { $minT = $ts }
    if ($end -gt $maxT) { $maxT = $end }
  }
}
$durSec = 0
if ($total -gt 0) { $durSec = ($maxT - $minT) / 1000.0; if ($durSec -le 0) { $durSec = 0.001 } }
$tps = 0
if ($total -gt 0) { $tps = [math]::Round($total / $durSec, 1) }

$dbOrders = (& $mysql -N -uroot -proot --host=127.0.0.1 --port=3306 seckill -e "SELECT COUNT(*) FROM t_seckill_order WHERE activity_id=$aid;" 2>$null | Select-Object -Last 1)
$dbStock = (& $mysql -N -uroot -proot --host=127.0.0.1 --port=3306 seckill -e "SELECT stock FROM t_seckill_stock WHERE activity_id=$aid;" 2>$null | Select-Object -Last 1)

Write-Output "SCHEME=$Mode THREADS=$Threads STOCK=$Stock AID=$aid"
Write-Output "JMETER_EXIT=$jmExit SAMPLES=$total ERRORS=$err DURATION_SEC=$([math]::Round($durSec,2)) TPS=$tps"
Write-Output "DB_ORDERS=$dbOrders DB_STOCK=$dbStock CONSISTENT=$($dbOrders -eq $Stock -and $dbStock -eq 0)"




