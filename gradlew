#!/bin/sh
# Gradle startup script

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

APP_HOME="`pwd -P`"
APP_BASE_NAME=`basename "$0"`
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
