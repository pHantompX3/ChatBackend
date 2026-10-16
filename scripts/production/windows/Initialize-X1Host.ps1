[CmdletBinding(SupportsShouldProcess)]
param([string]$Root = 'C:\ProgramData\WLChat')

$ErrorActionPreference = 'Stop'
$operator = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$directories = @('deployment', 'secrets', 'evidence', 'backup-staging', 'status', 'cloudflared')
foreach ($name in $directories) {
    $path = Join-Path $Root $name
    if ($PSCmdlet.ShouldProcess($path, 'Create restricted X1 directory')) {
        New-Item -ItemType Directory -Path $path -Force | Out-Null
        $acl = New-Object System.Security.AccessControl.DirectorySecurity
        $acl.SetAccessRuleProtection($true, $false)
        $inherit = [System.Security.AccessControl.InheritanceFlags]'ContainerInherit,ObjectInherit'
        $propagation = [System.Security.AccessControl.PropagationFlags]::None
        $acl.AddAccessRule((New-Object System.Security.AccessControl.FileSystemAccessRule(
            $operator, 'FullControl', $inherit, $propagation, 'Allow')))
        $acl.AddAccessRule((New-Object System.Security.AccessControl.FileSystemAccessRule(
            'NT AUTHORITY\SYSTEM', 'FullControl', $inherit, $propagation, 'Allow')))
        Set-Acl -Path $path -AclObject $acl
    }
}
Write-Output "X1 host directory layout is ready at $Root for $operator and SYSTEM."

