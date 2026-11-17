# Open interactive terminals so each server can be observed and stopped.
$ErrorActionPreference = 'Stop'
$backendLauncher = Join-Path $PSScriptRoot 'scripts\Start-Backend.ps1'
$frontendLauncher = Join-Path $PSScriptRoot 'scripts\Start-Frontend.ps1'
if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    throw 'Set JAVA_HOME to your JDK 21 directory before running this launcher.'
}
if ([string]::IsNullOrWhiteSpace($env:SPRING_DATASOURCE_USERNAME)) {
    $env:SPRING_DATASOURCE_USERNAME = 'instabazaar_runtime'
}
$promptedPassword = [string]::IsNullOrWhiteSpace($env:SPRING_DATASOURCE_PASSWORD)
try {
    if ($promptedPassword) {
        $privatePassword = Read-Host "Database password for $env:SPRING_DATASOURCE_USERNAME" -AsSecureString
        $env:SPRING_DATASOURCE_PASSWORD = [System.Net.NetworkCredential]::new('', $privatePassword).Password
    }
    Start-Process powershell.exe -ArgumentList @('-NoExit', '-File', ('"{0}"' -f $backendLauncher)) -WorkingDirectory $PSScriptRoot
    Start-Process powershell.exe -ArgumentList @('-NoExit', '-File', ('"{0}"' -f $frontendLauncher)) -WorkingDirectory $PSScriptRoot
    Write-Host 'Servers are starting in separate terminals. Open http://localhost:5173 when ready.'
} finally {
    if ($promptedPassword) { Remove-Item Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue }
    Remove-Variable privatePassword -ErrorAction SilentlyContinue
}
