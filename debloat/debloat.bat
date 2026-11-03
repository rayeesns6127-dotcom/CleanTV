@echo off
for /f "usebackq eol=# tokens=*" %%p in ("packages.txt") do (
  echo Removing %%p
  adb shell pm uninstall -k --user 0 %%p
)
pause
