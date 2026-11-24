param([switch]$Integration)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Java-Environment.ps1"
Push-Location $PSScriptRoot
try {
    $testArguments = @('-B', '-f', "$PSScriptRoot/backend/pom.xml")
    if ($Integration) { $testArguments += '-Dfirstday.integration=true' }
    $testArguments += 'test'
    & "$PSScriptRoot/backend/mvnw.cmd" @testArguments
    if ($LASTEXITCODE -ne 0) { throw 'Java tests failed. See backend/target/surefire-reports.' }
    Push-Location "$PSScriptRoot/frontend"
    try {
        if (-not (Test-Path 'node_modules')) {
            & npm.cmd ci
            if ($LASTEXITCODE -ne 0) { throw 'Frontend dependencies failed.' }
        }
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend type check or build failed.' }
    } finally { Pop-Location }
} finally { Pop-Location }
