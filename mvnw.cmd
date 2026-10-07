@ECHO OFF
REM MedMaint — project-local Maven wrapper (Windows).
SETLOCAL
SET "DIR=%~dp0"
FOR /D %%D IN ("%DIR%tools\apache-maven-*") DO SET "M2_HOME_DIR=%%D"
IF "%M2_HOME_DIR%"=="" (
  ECHO Telechargement de Maven 3.9.9...
  IF NOT EXIST "%DIR%tools" MKDIR "%DIR%tools"
  curl -sSL -o "%DIR%tools\maven.zip" "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip"
  tar -xf "%DIR%tools\maven.zip" -C "%DIR%tools"
  DEL "%DIR%tools\maven.zip"
  FOR /D %%D IN ("%DIR%tools\apache-maven-*") DO SET "M2_HOME_DIR=%%D"
)
IF "%JAVA_HOME%"=="" SET "JAVA_HOME=C:\Program Files\Java\jdk-24"
CALL "%M2_HOME_DIR%\bin\mvn.cmd" %*
ENDLOCAL
