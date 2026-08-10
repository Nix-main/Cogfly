param([int]$a, [string]$url, [string]$sha)
$exe = (Join-Path $PSScriptRoot "Cogfly-installer.exe")
Write-Output "Waiting for Cogfly to close..."
Wait-Process -Id $a -ErrorAction SilentlyContinue
Write-Output "Downloading Cogfly exe..."
Start-BitsTransfer -Source $url -Destination $exe -DisplayName "Downloading file"
Write-Output "Expecting hash " + $sha
$v = (Get-FileHash -Path $exe -Algorithm SHA256).Hash
if ($v -ne $sha.ToUpper())
{
    Write-Output "Mismatched hash."
    Remove-Item $exe -Force
    exit 1
}
Write-Output "Hash matched."
Start-Process $exe -ArgumentList "/SILENT /NORESTART" -Wait
Remove-Item $exe -Force
Start-Process "cogfly://"