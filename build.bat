@echo off
set VERSION=%1
if "%VERSION%"=="" set VERSION=1.0.0

:: use "call" because mvn itself is a cmd/bat file which would cause this script to stop after mvn
call mvn clean package
cd target
md package
move possnapshotter.jar package
cd package

jpackage ^
    --name "PosSnapshotter" ^
    --input . ^
    --main-jar possnapshotter.jar ^
    --description "Tool to take snapshots and print them to an ESC/POS printer." ^
    --win-menu ^
    --vendor "Flubba" ^
    --win-per-user-install ^
    --type "exe" ^
    --app-version "%VERSION%"
