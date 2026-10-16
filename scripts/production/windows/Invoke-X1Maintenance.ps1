[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('backup', 'audit-retention', 'diagnostics', 'certificate-renewal')][string]$Operation,
    [string]$WslDistribution,
    [string]$DeploymentRoot = '/mnt/c/ProgramData/WLChat/deployment'
)

$scripts = @{
    backup = './scripts/production/backup-production.sh'
    'audit-retention' = './scripts/production/purge-audit-events.sh'
    diagnostics = './scripts/production/collect-diagnostics.sh'
    'certificate-renewal' = './scripts/production/renew-private-tls.sh'
}
$distributionArgs = @()
if ($WslDistribution) { $distributionArgs = @('-d', $WslDistribution) }
& wsl @distributionArgs -- bash -lc "cd '$DeploymentRoot' && $($scripts[$Operation])"
if ($LASTEXITCODE -ne 0) { throw "$Operation failed with exit code $LASTEXITCODE" }
