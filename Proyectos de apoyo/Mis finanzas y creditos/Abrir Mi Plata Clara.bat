@echo off
setlocal
set "MI_PLATA_DIR=%~dp0"

where node.exe >nul 2>&1
if errorlevel 1 (
  echo No se encontro Node.js en este equipo.
  echo Instala Node.js 18 o superior y vuelve a abrir este archivo.
  pause
  exit /b 1
)

start "" powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -Command ^
  "$url = 'http://localhost:4173';" ^
  "$active = $false;" ^
  "try { Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 1 | Out-Null; $active = $true } catch {}" ^
  "if (-not $active) {" ^
  "  Start-Process -FilePath 'node.exe' -ArgumentList 'server.js' -WorkingDirectory $env:MI_PLATA_DIR -WindowStyle Hidden;" ^
  "  for ($attempt = 0; $attempt -lt 20; $attempt++) {" ^
  "    Start-Sleep -Milliseconds 250;" ^
  "    try { Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 1 | Out-Null; break } catch {}" ^
  "  }" ^
  "}" ^
  "if ($env:MI_PLATA_NO_BROWSER -ne '1') { Start-Process $url }"

exit /b 0
