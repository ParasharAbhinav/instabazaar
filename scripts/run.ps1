param([switch]$Demo, [switch]$Dev)

$ErrorActionPreference = 'Stop'
if ($Demo -and $Dev) { throw 'Choose either -Demo (H2) or -Dev (MySQL), not both.' }
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
Set-Location -LiteralPath $projectRoot

$environmentFile = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $environmentFile) {
    $allowed = @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'UPLOAD_DIR', 'BIND_ADDRESS', 'SESSION_COOKIE_SECURE', 'DEMO_PASSWORD', 'ADMIN_EMAIL', 'ADMIN_PASSWORD', 'PORT', 'VIDEO_MODEL_DIR', 'VIDEO_WORK_DIR', 'VIDEO_FFMPEG', 'VIDEO_FFPROBE')
    foreach ($line in Get-Content -LiteralPath $environmentFile) {
        if ($line -match '^\s*([A-Z_]+)=(.*)$' -and $allowed -contains $Matches[1]) {
            Set-Item -LiteralPath "Env:$($Matches[1])" -Value $Matches[2]
        }
    }
}

if (-not $env:JAVA_HOME -and -not (Get-Command java -ErrorAction SilentlyContinue)) {
    $portableTools = Join-Path $env:LOCALAPPDATA 'InstaBazaar\tools'
    if (Test-Path -LiteralPath $portableTools) {
        $jdk = Get-ChildItem -LiteralPath $portableTools -Directory -Filter 'jdk-21*' | Select-Object -First 1
        if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
    }
}
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME\bin;$env:PATH" }
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Install a Java 21 JDK and set JAVA_HOME before running.' }

$arguments = @('spring-boot:run')
if ($Demo) { $arguments += '-Dspring-boot.run.profiles=demo' }
if ($Dev) { $arguments += '-Dspring-boot.run.profiles=dev' }
& (Join-Path $projectRoot 'mvnw.cmd') @arguments
exit $LASTEXITCODE
