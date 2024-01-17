rem https://garimanishad.medium.com/
rem ##############################################
rem Assuming emulator or device is already attached
rem ##############################################

@echo off

rem set app id
set appId=com.mahamsoft.expenseviewapp
set startActivity=MainActivity

rem ########################################################################
rem DO NOT EDIT UNLESS SURE
rem ########################################################################
set adb="E:\SDExtract\stuff\adb.exe"

rem check running devices
%adb% devices

set res=N
set /p "runBuild=Do you want to run gradle build?:"

if "%runBuild%"=="y" set res=Y
if "%runBuild%"=="Y" set res=Y
if "%runBuild%"=="YES" set res=Y
if "%runBuild%"=="yes" set res=Y
if "%runBuild%"=="yes" set res=Y

if "%res%"=="Y" (
	rem build app
	gradlew.bat build
)

rem upload apk to device
%adb% shell pm uninstall -k --user 0 %appId%
%adb% install -r .\app\release\app-release.apk

rem run logcat
rem %adb% logcat

rem start app
%adb% shell am start -n "%appId%/%appId%.%startActivity%" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER

