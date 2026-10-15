[CmdletBinding()]
param([int]$MinimumFreeGb = 80)

$ErrorActionPreference = 'Stop'
$failures = [System.Collections.Generic.List[string]]::new()
function Test-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) { $failures.Add("Missing command: $Name") }
}

$os = Get-CimInstance Win32_OperatingSystem
if ($os.Caption -notmatch 'Windows 11 Pro') { $failures.Add("Expected Windows 11 Pro; found $($os.Caption)") }
$systemDrive = Get-Volume -DriveLetter $env:SystemDrive.TrimEnd(':')
if (($systemDrive.SizeRemaining / 1GB) -lt $MinimumFreeGb) { $failures.Add("Less than $MinimumFreeGb GB free") }
if ((Get-BitLockerVolume -MountPoint $env:SystemDrive).ProtectionStatus -ne 'On') { $failures.Add('BitLocker protection is not On') }

Test-Command docker
Test-Command wsl
Test-Command sshd
try { docker version --format '{{.Server.Version}}' | Out-Null } catch { $failures.Add('Docker Engine is not ready') }
try { docker compose version | Out-Null } catch { $failures.Add('Docker Compose is unavailable') }
$wslState = wsl --list --verbose 2>&1 | Out-String
if ($wslState -notmatch '\s+2\s') { $failures.Add('No WSL2 distribution was detected') }
$timeService = Get-Service W32Time -ErrorAction SilentlyContinue
if (-not $timeService -or $timeService.Status -ne 'Running') { $failures.Add('Windows Time service is not running') }
$sshService = Get-Service sshd -ErrorAction SilentlyContinue
if (-not $sshService) { $failures.Add('OpenSSH Server service is not installed') }

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ }
    exit 1
}
Write-Output "X1 prerequisite checks passed: $($os.Caption); free=$([math]::Round($systemDrive.SizeRemaining / 1GB, 1))GB."

