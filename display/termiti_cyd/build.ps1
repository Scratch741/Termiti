# Sestaví firmware a (bez -NoUpload) nahraje ho do CYD.
#   .\build.ps1              # COM3
#   .\build.ps1 -Port COM5
#   .\build.ps1 -NoUpload
param(
  [string]$Port = "COM3",
  [switch]$NoUpload
)
$ErrorActionPreference = "Stop"

$cli = (Get-Command arduino-cli -ErrorAction SilentlyContinue).Source
if (-not $cli) { $cli = "C:\Program Files\Arduino IDE\resources\app\lib\backend\resources\arduino-cli.exe" }

$sketch = $PSScriptRoot
$fqbn   = "esp32:esp32:esp32:PartitionScheme=huge_app"

# lv_conf.h leží vedle sketche; TFT_eSPI se nastavuje tady, ne v User_Setup.h knihovny
$flags = @(
  "-DLV_CONF_INCLUDE_SIMPLE", "-I$($sketch -replace '\\', '/')",
  "-DUSER_SETUP_LOADED=1", "-DILI9341_2_DRIVER=1",
  "-DTFT_WIDTH=240", "-DTFT_HEIGHT=320",
  "-DTFT_MISO=12", "-DTFT_MOSI=13", "-DTFT_SCLK=14", "-DTFT_CS=15", "-DTFT_DC=2", "-DTFT_RST=-1",
  "-DUSE_HSPI_PORT=1", "-DSPI_FREQUENCY=55000000", "-DSPI_READ_FREQUENCY=20000000"
) -join " "

$cliArgs = @(
  "compile", "--fqbn", $fqbn,
  "--build-path", (Join-Path $sketch "build"),
  "--build-property", "compiler.c.extra_flags=$flags",
  "--build-property", "compiler.cpp.extra_flags=$flags",
  "--warnings", "none"
)
if (-not $NoUpload) { $cliArgs += @("--upload", "--port", $Port) }
$cliArgs += $sketch

& $cli @cliArgs
if ($LASTEXITCODE -ne 0) { throw "arduino-cli skončil s chybou $LASTEXITCODE" }
