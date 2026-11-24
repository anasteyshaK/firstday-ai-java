$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME/bin/java.exe")) {
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCommand) {
        $env:JAVA_HOME = Split-Path (Split-Path $javaCommand.Source)
    } else {
        $bundledJava = Get-ChildItem 'C:/Program Files/JetBrains' -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            Where-Object { Test-Path (Join-Path $_.FullName 'jbr/bin/java.exe') } |
            Select-Object -First 1
        if ($bundledJava) { $env:JAVA_HOME = Join-Path $bundledJava.FullName 'jbr' }
    }
}
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME/bin/java.exe")) {
    throw 'Install JDK 21 or newer and set JAVA_HOME to its installation directory.'
}

$env:Path = "$env:JAVA_HOME/bin;" + $env:Path
