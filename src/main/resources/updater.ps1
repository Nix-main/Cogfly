param([int]$a, [string]$url)
$exe = (Join-Path $PSScriptRoot "Cogfly-installer.exe")
echo "Waiting for Cogfly to close..."
Wait-Process -Id $a -ErrorAction SilentlyContinue
echo "Download Cogfly exe..."
Start-BitsTransfer -Source $url -Destination $exe -DisplayName "Downloading file"
echo "Downloaded!"
Start-Process $exe
Remove-Item $exe