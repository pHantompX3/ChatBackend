[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$WrapperPath,
    [string]$TaskPrefix = 'WLChat'
)

$ErrorActionPreference = 'Stop'
$currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
function Install-Task([string]$Name, [string]$Operation, $Trigger) {
    $arguments = "-NoProfile -NonInteractive -ExecutionPolicy RemoteSigned -File `"$WrapperPath`" -Operation $Operation"
    $action = New-ScheduledTaskAction -Execute 'PowerShell.exe' -Argument $arguments
    $settingsParameters = @{
        StartWhenAvailable  = $true
        ExecutionTimeLimit = (New-TimeSpan -Hours 2)
        RestartCount       = 2
        RestartInterval    = (New-TimeSpan -Minutes 10)
    }
    $settings = New-ScheduledTaskSettingsSet @settingsParameters
    $registrationParameters = @{
        TaskName = "$TaskPrefix-$Name"
        Action   = $action
        Trigger  = $Trigger
        Settings = $settings
        User     = $currentUser
        RunLevel = 'Highest'
        Force    = $true
    }
    Register-ScheduledTask @registrationParameters | Out-Null
}

Install-Task 'DailyBackup' 'backup' (New-ScheduledTaskTrigger -Daily -At '02:00')
Install-Task 'WeeklyAuditRetention' 'audit-retention' (New-ScheduledTaskTrigger -Weekly -WeeksInterval 1 -DaysOfWeek Sunday -At '03:30')
Install-Task 'MonthlyOriginCertificateRenewal' 'certificate-renewal' (New-ScheduledTaskTrigger -Weekly -WeeksInterval 4 -DaysOfWeek Sunday -At '04:30')
Write-Output 'Installed X1 backup, retention, and private-origin certificate tasks with bounded retries.'
