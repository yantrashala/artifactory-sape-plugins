Artifactory Plugins
===============================

A collection of [groovy user plugins] for [Artifactory].

[Artifactory]: http://artifactory.jfrog.org
[groovy user plugins]: http://wiki.jfrog.org/confluence/display/RTF/User+Plugins

NOTE-

sonar.groovy and backupmodulepermissions.groovy plugins requires 3rd party jars in /app/artifactory/tomcat/webapps/artifactory/WEB-INF/lib.

How to upate 3rd party jars for sonar.groovy and backupmodulepermissions.groovy plugins
--------------------------------------------------------------------------------------------------------------------
- Download the jars from the below urls
	http://central.maven.org/maven2/net/sf/opencsv/opencsv/2.3/opencsv-2.3.jar

	http://central.maven.org/maven2/org/codehaus/sonar/sonar-ws-client/5.1/sonar-ws-client-5.1.jar
  
- Upload the jars to /app/artifactory/tomcat/webapps/artifactory/WEB-INF/lib

- Make sure the owner of the jars should be artifactory
