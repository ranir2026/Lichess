@echo off
rem Launches the UCI front end from the compiled classes next to this script.
rem Java is located in this order: JAVA_HOME, the portable JDK in %USERPROFILE%\.jdks\temurin-21.0.12, then PATH.
set "JAVA_EXE=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if "%JAVA_EXE%"=="java" if exist "%USERPROFILE%\.jdks\temurin-21.0.12\bin\java.exe" set "JAVA_EXE=%USERPROFILE%\.jdks\temurin-21.0.12\bin\java.exe"
"%JAVA_EXE%" -cp "%~dp0." UciInterface
