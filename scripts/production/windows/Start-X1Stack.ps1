[CmdletBinding()]
param(
    [string]$WslDistribution,
    [string]$DeploymentRoot = '/mnt/c/ProgramData/WLChat/deployment',
    [int]$Attempts = 30
)

$ErrorActionPreference = 'Stop'
for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
    try { docker info | Out-Null; break } catch {
        if ($attempt -eq $Attempts) { throw 'Docker did not become ready within the bounded retry window.' }
        Start-Sleep -Seconds 10
    }
}
$distributionArgs = @()
if ($WslDistribution) { $distributionArgs = @('-d', $WslDistribution) }
& wsl @distributionArgs -- bash -lc "cd '$DeploymentRoot' && ./scripts/production/deploy-production.sh"
if ($LASTEXITCODE -ne 0) { throw "Production stack startup failed with exit code $LASTEXITCODE" }

