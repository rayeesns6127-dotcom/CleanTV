@echo off
rem Usage: restore.bat com.package.name
adb shell cmd package install-existing %1
