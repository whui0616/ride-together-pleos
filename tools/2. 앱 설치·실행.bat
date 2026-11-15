@echo off
chcp 65001 >nul
title car-pet 설치·실행
set ADB=C:\Android\Sdk\platform-tools\adb.exe
echo 에뮬레이터 부팅 확인 중...
:wait
for /f "delims=" %%b in ('"%ADB%" shell getprop sys.boot_completed 2^>nul') do set BOOT=%%b
if not "%BOOT%"=="1" ( timeout /t 5 /nobreak >nul & goto wait )
echo 빌드 + 설치 중 (처음엔 몇 분 걸려요)...
cd /d C:\dev\car
call gradlew.bat :app:installDebug --console=plain "-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=COMPAT"
if errorlevel 1 ( echo. & echo 빌드 실패. 위 메시지를 캡처해서 팀에 공유해 주세요. & pause & exit /b 1 )
"%ADB%" shell am start -n ai.nunchi.mate/.MainActivity >nul
echo.
echo 설치 완료. 에뮬레이터 하단 바에서 파란 자동차 얼굴 아이콘을 누르면 앱이 앞으로 나와요.
timeout /t 8
