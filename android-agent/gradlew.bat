@ECHO OFF
SET DIR=%~dp0
java %JAVA_OPTS% -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
