@echo off
setlocal

set "ROOT=%~dp0.."
for %%I in ("%ROOT%") do set "ROOT=%%~fI"

call "%ROOT%\scripts\load-toolchain.bat" "%ROOT%"
if errorlevel 1 exit /b 1

if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Building access-unpack...
"%MVN_CMD%" -DskipTests package %*
if errorlevel 1 exit /b 1

echo Build complete.
echo Artifacts:
powershell -NoProfile -NonInteractive -Command "Get-ChildItem -Path '%ROOT%' -Recurse -File -Filter *.jar | Where-Object { $_.FullName -match '\\access-unpack-[^\\]+\\target\\[^\\]+\.jar$' } | Sort-Object FullName | ForEach-Object { $_.FullName }"
exit /b 0
