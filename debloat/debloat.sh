#!/bin/sh
grep -v '^#' packages.txt | grep -v '^$' | while read p; do
  echo "Removing $p"; adb shell pm uninstall -k --user 0 "$p"
done
