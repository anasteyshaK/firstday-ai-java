$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Java-Environment.ps1"
Push-Location $PSScriptRoot
try {
    & "$PSScriptRoot/backend/mvnw.cmd" -B -f "$PSScriptRoot/backend/pom.xml" package
    if ($LASTEXITCODE -ne 0) { throw 'Backend build or tests failed.' }
} finally { Pop-Location }
