@echo off
setlocal

set "ROOT=%~1"
if "%ROOT%"=="" set "ROOT=%~dp0.."
for %%I in ("%ROOT%") do set "ROOT=%%~fI"

set "OUTPUT_FILE=%TEMP%\access-unpack-toolchain-%RANDOM%%RANDOM%.env"
powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%ROOT%\scripts\ensure-toolchain.ps1" -Root "%ROOT%" > "%OUTPUT_FILE%"
if errorlevel 1 (
  if exist "%OUTPUT_FILE%" del "%OUTPUT_FILE%" >nul 2>&1
  endlocal & exit /b 1
)

set "JAVA_CMD="
set "MVN_CMD="
set "JAVA_HOME="
set "TOOLS_DIR="

for /f "usebackq tokens=1* delims==" %%A in ("%OUTPUT_FILE%") do (
  if /I "%%A"=="JAVA_CMD" set "JAVA_CMD=%%B"
  if /I "%%A"=="MVN_CMD" set "MVN_CMD=%%B"
  if /I "%%A"=="JAVA_HOME" set "JAVA_HOME=%%B"
  if /I "%%A"=="TOOLS_DIR" set "TOOLS_DIR=%%B"
)

del "%OUTPUT_FILE%" >nul 2>&1

if not defined JAVA_CMD (
  >&2 echo Error: failed to resolve Java command.
  endlocal & exit /b 1
)
if not defined MVN_CMD (
  >&2 echo Error: failed to resolve Maven command.
  endlocal & exit /b 1
)

endlocal & (
  set "JAVA_CMD=%JAVA_CMD%"
  set "MVN_CMD=%MVN_CMD%"
  set "JAVA_HOME=%JAVA_HOME%"
  set "TOOLS_DIR=%TOOLS_DIR%"
)
exit /b 0
