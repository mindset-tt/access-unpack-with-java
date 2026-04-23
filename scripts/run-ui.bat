@echo off
setlocal

set "ROOT=%~dp0.."
for %%I in ("%ROOT%") do set "ROOT=%%~fI"

powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%ROOT%\scripts\run-ui.ps1" -Root "%ROOT%"
exit /b %errorlevel%
