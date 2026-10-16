[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$ProductionAddress = '192.168.0.199',
    [Parameter(Mandatory)][string]$AdministrationSource
)

$ErrorActionPreference = 'Stop'
if ($AdministrationSource -notmatch '^[0-9a-fA-F:.]+(/[0-9]{1,3})?$') { throw 'Unsafe administration source.' }
$group = 'WL Chat Production'
if ($PSCmdlet.ShouldProcess($group, 'Replace ChatBackend firewall rules')) {
    Get-NetFirewallRule -Group $group -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    Get-NetFirewallRule -Name 'OpenSSH-Server-In-TCP*' -ErrorAction SilentlyContinue |
        Where-Object { $_.Group -ne $group } |
        Disable-NetFirewallRule
    $sshRuleParameters = @{
        DisplayName    = 'WL Chat LAN SSH'
        Group          = $group
        Direction      = 'Inbound'
        Action         = 'Allow'
        Protocol       = 'TCP'
        LocalPort      = 22
        LocalAddress   = $ProductionAddress
        RemoteAddress  = $AdministrationSource
        Profile        = 'Private'
    }
    New-NetFirewallRule @sshRuleParameters | Out-Null
    foreach ($port in 80, 443, 1433, 15672, 5672, 2375, 2376) {
        $blockRuleParameters = @{
            DisplayName = "WL Chat block inbound TCP $port"
            Group       = $group
            Direction   = 'Inbound'
            Action      = 'Block'
            Protocol    = 'TCP'
            LocalPort   = $port
            Profile     = 'Any'
        }
        New-NetFirewallRule @blockRuleParameters | Out-Null
    }
}
Write-Output 'Applied deny-first X1 inbound rules; Cloudflare Tunnel requires outbound access only.'
