@echo off
title Pleos 에뮬레이터
set ANDROID_SDK_ROOT=C:\Android\Sdk
set ANDROID_HOME=C:\Android\Sdk
set ANDROID_AVD_HOME=C:\Android\avd
set PATH=C:\Android\Sdk\platform-tools;C:\Android\Sdk\emulator;%PATH%
echo Pleos_Connect 에뮬레이터를 켭니다. 부팅까지 1분쯤 걸려요. 이 창은 닫아도 됩니다.
start "" "C:\Android\Sdk\emulator\emulator.exe" -avd Pleos_Connect -gpu host
