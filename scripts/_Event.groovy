eventCompileStart = {target ->

    // make sure that our ast transformation and other classes get compiled first
    if (grailsAppName == "gorm-riak") {

        // don't need to do this more than once
        if (getBinding().variables.containsKey("_gorm_riak_compile_called")) return
            _gorm_riak_compile_called = true

        ant.sequential {
            echo "Compiling gorm-riak plugin..."
            
            path id: "grails.compile.classpath", compileClasspath
            mkdir dir: classesDirPath

            def classpathId = "grails.compile.classpath"

            groovyc(destdir: classesDirPath,
                    projectName: grailsSettings.baseDir.name,
                    classpathref: classpathId,
                    encoding: "UTF-8") {

                src(path: "${basedir}/src/groovy")
                src(path: "${basedir}/src/java")
                
                javac(classpathref: classpathId, debug: "yes")
            }
        }
    }
}

