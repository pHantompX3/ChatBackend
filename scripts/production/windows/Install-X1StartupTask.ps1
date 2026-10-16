[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ScriptPath,
    [string]$TaskName = 'WLChat-Start-Production'
)

$ErrorActionPreference = 'Stop'
$currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$action = New-ScheduledTaskAction -Execute 'PowerShell.exe' -Argument "-NoProfile -NonInteractive -ExecutionPolicy RemoteSigned -File `"$ScriptPath`""
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $currentUser
$settingsParameters = @{
    StartWhenAvailable  = $true
    ExecutionTimeLimit = (New-TimeSpan -Minutes 20)
    RestartCount       = 2
    RestartInterval    = (New-TimeSpan -Minutes 2)
}
$settings = New-ScheduledTaskSettingsSet @settingsParameters
$registrationParameters = @{
    TaskName = $TaskName
    Action   = $action
    Trigger  = $trigger
    Settings = $settings
    User     = $currentUser
    RunLevel = 'Highest'
    Force    = $true
}
Register-ScheduledTask @registrationParameters | Out-Null
Write-Output "Installed bounded at-logon startup task $TaskName for $currentUser."
