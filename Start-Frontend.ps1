$ErrorActionPreference = 'Stop'
Push-Location "$PSScriptRoot/frontend"
try {
    if (-not (Test-Path 'node_modules')) {
        & npm.cmd ci
        if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
    }
    & npm.cmd run dev -- --strictPort
    if ($LASTEXITCODE -ne 0) { throw 'Frontend stopped with an error.' }
} finally { Pop-Location }
