param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$Profile = 'dev'
)
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path $PSScriptRoot -Parent
$backendDirectory = Join-Path $repositoryRoot 'backend'
if ([string]::IsNullOrWhiteSpace($JavaHome) -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) {
    throw 'Provide your JDK 21 directory with -JavaHome or JAVA_HOME.'
}
$env:JAVA_HOME = $JavaHome
if ([string]::IsNullOrWhiteSpace($env:SPRING_DATASOURCE_URL)) { $env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://127.0.0.1:5432/instabazaar' }
if ([string]::IsNullOrWhiteSpace($env:SPRING_DATASOURCE_USERNAME)) { $env:SPRING_DATASOURCE_USERNAME = 'instabazaar_runtime' }
$promptedPassword = [string]::IsNullOrWhiteSpace($env:SPRING_DATASOURCE_PASSWORD)
if ($promptedPassword) {
    $privatePassword = Read-Host "Database password for $env:SPRING_DATASOURCE_USERNAME" -AsSecureString
    $env:SPRING_DATASOURCE_PASSWORD = [System.Net.NetworkCredential]::new('', $privatePassword).Password
}
Push-Location $backendDirectory
try {
    & '.\mvnw.cmd' spring-boot:run "-Dspring-boot.run.profiles=$Profile"
    if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE." }
} finally {
    Pop-Location
    if ($promptedPassword) { Remove-Item Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue }
    Remove-Variable privatePassword -ErrorAction SilentlyContinue
}
