@echo off
chcp 65001 >nul
title So Lop - Web (MySQL)
cd /d "%~dp0"
echo.
echo ============================================
echo   SO LOP - BAN WEB (MySQL)
echo ============================================
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run-web.ps1"
set "CODE=%ERRORLEVEL%"
echo.
if not "%CODE%"=="0" echo Co loi khi chay (ma %CODE%). Xem thong bao o tren.
echo Nhan phim bat ky de dong cua so...
pause >nul
exit /b %CODE%
