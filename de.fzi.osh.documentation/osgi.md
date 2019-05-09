# OSGI

> **Note:**
OSGi (Open Service Gateway Initiative) is a Java framework for developing and deploying modular software programs and libraries. Each bundle is a tightly coupled, dynamically loadable collection of classes, jars, and configuration files that explicitly declare their external dependencies (if any). [[Wiki]](https://en.wikipedia.org/wiki/OSGi)

[Introduction](http://njbartlett.name/osgibook.html)

### Classloaders
http://moi.vonos.net/java/osgi-classloaders/

### Declarative Services
[http://blog.vogella.com/2016/06/21/getting-started-with-osgi-declarative-services/comment-page-1/](http://blog.vogella.com/2016/06/21/getting-started-with-osgi-declarative-services/comment-page-1/)

## Setting up a Project in Eclipse JEE

 1. Create "Plugin-Project"
 2. Framework "OSGI Standard"

Running:

	In Eclipse:		
		Run As: "OSGi Framework"		
		Required Bundles (minimal setup): 
			org.apache.felix.gogo.command
			org.apache.felix.gogo.runtime
			org.apache.felix.gogo.shell
			org.eclipse.equinox.console

	Standalone:
		Directory:
			- configuration/config.ini
			- org.eclipse.osgi.jar
			- org.apache.felix.gogo.command.jar
			- org.apache.felix.gogo.runtime.jar
			- org.apache.felix.gogo.shell.jar
			- org.eclipse.equinox.console.jar
	
		java -Declipse.ignoreApp=true -jar org.eclipse.osgi.jar -console -consoleLog
	
		Running Configuration [file: ./configuration/config.ini]		
			# required bundles
			osgi.bundles=org.apache.felix.gogo.command@start , org.apache.felix.gogo.runtime@start , org.apache.felix.gogo.shell@start , org.eclipse.equinox.console@start
			# telnet port, when not using "-console" command
			osgi.console=12345
		
		In console: install file:\bundle.jar and start	

Start Levels:	
	http://eclipsesource.com/blogs/2009/06/10/osgi-and-start-levels/
