#!/bin/sh
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -n "$JAVA_HOME" ]; then JAVA="$JAVA_HOME/bin/java"; else JAVA=java; fi
if [ ! -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
  echo "Gradle wrapper JAR is missing. Download it from Gradle 8.9 or use an installed Gradle 8.9+ executable." >&2
  exit 1
fi
exec "$JAVA" -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
