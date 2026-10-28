# Saves the launch command of a running Minecraft (e.g. from the Modrinth App) for prodscene.py, with the login
# token, xuid and client id replaced by 0: singleplayer works offline, and no credential ever lands on disk.
# Usage: grab_cmdline.ps1 -ProcessId <pid> [-Out tools/live/prod_cmdline.txt]
param(
    [int]$ProcessId,
    [string]$Out = "$PSScriptRoot\prod_cmdline.txt"
)
$c = (Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId").CommandLine
if (-not $c) { Write-Output "no process $ProcessId"; exit 1 }
$c = $c -replace '(--accessToken\s+)\S+', '${1}0' -replace '(--xuid\s+)\S+', '${1}0' -replace '(--clientId\s+)\S+', '${1}0'
Set-Content -Path $Out -Value $c -Encoding utf8 -NoNewline
Write-Output "saved to $Out"
