$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Java-Environment.ps1"
Push-Location $PSScriptRoot
try {
    $jar = Join-Path $PSScriptRoot 'backend/target/firstday-ai-1.0.0.jar'
    if (-not (Test-Path $jar)) { & "$PSScriptRoot/Build-Backend.ps1" }
    & "$env:JAVA_HOME/bin/java.exe" -jar $jar
    if ($LASTEXITCODE -ne 0) { throw 'Java backend stopped with an error.' }
} finally { Pop-Location }
