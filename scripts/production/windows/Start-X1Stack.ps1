[CmdletBinding()]
param(
    [string]$WslDistribution,
    [string]$DeploymentRoot = '/mnt/c/ProgramData/WLChat/deployment',
    [int]$Attempts = 30
)

$ErrorActionPreference = 'Stop'
for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
    docker info 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { break }
    if ($attempt -eq $Attempts) { throw 'Docker did not become ready within the bounded retry window.' }
    Start-Sleep -Seconds 10
}
$distributionArgs = @()
if ($WslDistribution) { $distributionArgs = @('-d', $WslDistribution) }
& wsl @distributionArgs -- bash -lc "cd '$DeploymentRoot' && export WL_CHAT_PRODUCTION_ENV_FILE='$DeploymentRoot/production.env' && ./scripts/production/deploy-production.sh"
if ($LASTEXITCODE -ne 0) { throw "Production stack startup failed with exit code $LASTEXITCODE" }
