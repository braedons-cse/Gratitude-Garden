@echo off
rem Double-click to boot the emulator, install the staging debug build, and open the app.
rem Any arguments are passed through to scripts\run-emulator.ps1, e.g.
rem   Start-Emulator.bat -Consumer
rem   Start-Emulator.bat -SkipInstall
setlocal
cd /d "%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run-emulator.ps1" %*
set "EXITCODE=%ERRORLEVEL%"

echo.
if not "%EXITCODE%"=="0" (
    echo run-emulator.ps1 failed with exit code %EXITCODE%.
) else (
    echo Done. The emulator keeps running after this window closes.
)
pause

endlocal
exit /b %EXITCODE%
