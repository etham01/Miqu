#!/usr/bin/env bash
# Maven wrapper workaround: the stock mvn shell script fails in this sandbox
# ("ClassNotFoundException: org.codehaus.plexus.classworlds.launcher.Launcher"),
# so invoke the launcher class directly via java.
export PATH="/c/Users/张志鹏/.workbuddy/binaries/PortableGit/versions/1.2.0/usr/bin:/c/Users/张志鹏/.workbuddy/binaries/PortableGit/versions/1.2.0/bin:$PATH"
export JAVA_HOME=/d/soft/java/Java/jdk-21
M=/d/soft/apache-maven-3.9.14-bin/apache-maven-3.9.14
MW=$(cygpath -w "$M")
json=$(cygpath -w "$M/boot/plexus-classworlds-2.9.0.jar")
cw=$(cygpath -w "$M/bin/m2.conf")
exec "$JAVA_HOME/bin/java" -cp "$json" \
  -Dclassworlds.conf="$cw" \
  -Dmaven.home="$MW" \
  -Dmaven.multiModuleProjectDirectory="$(cygpath -w "$PWD")" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
