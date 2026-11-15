@echo off
title Pleos Virtual Console
"C:\Android\Sdk\platform-tools\adb.exe" forward tcp:8088 tcp:8088
start "" http://127.0.0.1:8088
