<#
.SYNOPSIS
  Prepares installer assets: downloads Eclipse Temurin 21 JRE and
  generates rasoi.ico from rasoi.png.
  Called automatically by build-installer.bat.
#>
param(
    [Parameter(Mandatory)][string] $JreDir,
    [Parameter(Mandatory)][string] $PngPath,
    [Parameter(Mandatory)][string] $IcoPath
)

$ErrorActionPreference = "Stop"

# ── JRE ───────────────────────────────────────────────────────────────────────
if (Test-Path "$JreDir\bin\java.exe") {
    Write-Host "[JRE] Cached JRE found at installer\jre — skipping download."
} else {
    Write-Host "[JRE] Downloading Eclipse Temurin 21 JRE for Windows x64..."
    $url = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse"
    $zip = Join-Path $env:TEMP "temurin21-jre.zip"
    $tmp = Join-Path $env:TEMP "jre_extract_$(Get-Random)"

    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
    Write-Host "[JRE] Downloaded $([Math]::Round((Get-Item $zip).Length / 1MB, 1)) MB."

    Write-Host "[JRE] Extracting..."
    Expand-Archive -Path $zip -DestinationPath $tmp -Force
    $top = (Get-ChildItem $tmp | Select-Object -First 1).FullName
    if (-not (Test-Path $JreDir)) { New-Item -ItemType Directory -Path $JreDir | Out-Null }
    Copy-Item "$top\*" $JreDir -Recurse -Force
    Remove-Item $tmp -Recurse -Force
    Remove-Item $zip  -Force
    Write-Host "[JRE] Done."
}

# ── Icon ──────────────────────────────────────────────────────────────────────
if (Test-Path $IcoPath) {
    Write-Host "[ICO] Icon already exists — skipping."
} else {
    Write-Host "[ICO] Generating rasoi.ico from rasoi.png..."
    Add-Type -AssemblyName System.Drawing
    $bmp    = New-Object System.Drawing.Bitmap($PngPath)
    $handle = $bmp.GetHicon()
    $icon   = [System.Drawing.Icon]::FromHandle($handle)
    $stream = [System.IO.File]::OpenWrite($IcoPath)
    $icon.Save($stream)
    $stream.Dispose()
    $icon.Dispose()
    $bmp.Dispose()
    Write-Host "[ICO] Saved to $IcoPath."
}
