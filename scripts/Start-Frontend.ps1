$ErrorActionPreference = 'Stop'
$frontendDirectory = Join-Path (Split-Path $PSScriptRoot -Parent) 'frontend'
Push-Location $frontendDirectory
try {
    & npm.cmd run dev
    if ($LASTEXITCODE -ne 0) { throw "Frontend exited with code $LASTEXITCODE." }
} finally { Pop-Location }
