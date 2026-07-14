param([int]$a, [string]$url, [string]$sha)
$exe = (Join-Path $PSScriptRoot "Cogfly-installer.exe")
echo "Waiting for Cogfly to close..."
Wait-Process -Id $a -ErrorAction SilentlyContinue
echo "Download Cogfly exe..."
Start-BitsTransfer -Source $url -Destination $exe -DisplayName "Downloading file"
echo "Expecting hash " + $sha
$v = (Get-FileHash -Path $exe -Algorithm SHA256).Hash
if ($v -ne $sha.ToUpper())
{
    echo "Mismatched hash."
    Remove-Item $exe -Force
    exit 1
}
echo "Hash matched."
Start-Process $exe -ArgumentList "/SILENT /NORESTART" -Wait
Remove-Item $exe -Force