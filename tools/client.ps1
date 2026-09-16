# Starts and stops the development client.
#
# Gradle's runClient launches Minecraft as a child java process, and killing the gradle invocation
# does not kill it. Worse, wmic is gone from Windows 11, so the obvious way of finding that process
# fails silently and leaves a second client running beside the first. This finds it properly, by
# the launcher class in its command line.
#
#   powershell -NoProfile -File tools/client.ps1 stop
#   powershell -NoProfile -File tools/client.ps1 start

param([Parameter(Mandatory = $true)][ValidateSet('start', 'stop', 'status', 'bare')][string]$action)

$project = Split-Path -Parent $PSScriptRoot

# In a development run the mod is not a jar in a folder: the loader finds it by the fabric.mod.json
# lying on the classpath. Move that one file aside and the same client starts with no mod at all,
# which is the only honest way to see a book the way somebody without the mod sees it. Gradle would
# put the file straight back, so the run skips the task that writes it.
$manifest = Join-Path $project 'build/resources/main/fabric.mod.json'
$parked = "$manifest.off"

function Set-Mod([bool]$wanted) {
    if ($wanted -and (Test-Path $parked)) {
        Move-Item -Force $parked $manifest
    } elseif (-not $wanted -and (Test-Path $manifest)) {
        Move-Item -Force $manifest $parked
    }
}

function Get-Clients {
    Get-CimInstance Win32_Process -Filter "Name='java.exe' or Name='javaw.exe'" |
        Where-Object { $_.CommandLine -like '*KnotClient*' -or $_.CommandLine -like '*devlaunch*' }
}

switch ($action) {
    'stop' {
        $found = Get-Clients
        if (-not $found) {
            Write-Output 'no client running'
            break
        }
        foreach ($process in $found) {
            Stop-Process -Id $process.ProcessId -Force
            Write-Output ("stopped " + $process.ProcessId)
        }
        Start-Sleep -Seconds 2
    }
    'start' {
        if (Get-Clients) {
            Write-Output 'a client is already running; stop it first'
            break
        }
        Set-Mod $true
        Push-Location $project
        Start-Process -FilePath (Join-Path $project 'gradlew.bat') `
            -ArgumentList 'runClient', '--console=plain' `
            -RedirectStandardOutput (Join-Path $project 'run-client.log') `
            -RedirectStandardError (Join-Path $project 'run-client.err.log') `
            -WindowStyle Hidden
        Pop-Location
        Write-Output 'starting'
    }
    'bare' {
        if (Get-Clients) {
            Write-Output 'a client is already running; stop it first'
            break
        }
        Set-Mod $false
        Push-Location $project
        Start-Process -FilePath (Join-Path $project 'gradlew.bat') `
            -ArgumentList 'runClient', '--console=plain', '-x', 'processResources' `
            -RedirectStandardOutput (Join-Path $project 'run-client.log') `
            -RedirectStandardError (Join-Path $project 'run-client.err.log') `
            -WindowStyle Hidden
        Pop-Location
        Write-Output 'starting without the mod'
    }
    'status' {
        $found = Get-Clients
        Write-Output ("running: " + @($found).Count)
    }
}
