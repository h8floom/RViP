@echo off
setlocal
set GRADLE_VERSION=9.7.1
if not defined GRADLE_USER_HOME set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set INSTALL_DIR=%GRADLE_USER_HOME%\course-gradle\gradle-%GRADLE_VERSION%
set GRADLE_BIN=%INSTALL_DIR%\bin\gradle.bat

if not exist "%GRADLE_BIN%" (
  if not exist "%GRADLE_USER_HOME%\course-gradle" mkdir "%GRADLE_USER_HOME%\course-gradle"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $v='%GRADLE_VERSION%'; $base='%GRADLE_USER_HOME%\course-gradle'; $zip=Join-Path $base ('gradle-' + $v + '-bin.zip'); Invoke-WebRequest -Uri ('https://services.gradle.org/distributions/gradle-' + $v + '-bin.zip') -OutFile $zip; Expand-Archive -Path $zip -DestinationPath $base -Force; Remove-Item $zip"
  if errorlevel 1 exit /b 1
)

call "%GRADLE_BIN%" %*
exit /b %ERRORLEVEL%
