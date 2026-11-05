@echo off
set APP_HOME=%~dp0
if not exist "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" (
  echo Gradle wrapper JAR is missing. Download it from Gradle 8.9 or use an installed Gradle 8.9+ executable.
  exit /b 1
)
if defined JAVA_HOME (set JAVA=%JAVA_HOME%\bin\java.exe) else (set JAVA=java.exe)
"%JAVA%" -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
