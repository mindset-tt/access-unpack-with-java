@echo off
setlocal

set "ROOT=%~dp0.."
for %%I in ("%ROOT%") do set "ROOT=%%~fI"

powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%ROOT%\scripts\run-cli.ps1" -Root "%ROOT%" -CliArgs %*
exit /b %errorlevel%
