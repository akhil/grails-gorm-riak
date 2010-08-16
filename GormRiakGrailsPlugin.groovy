import org.codehaus.groovy.grails.plugins.riak.RiakPluginSupport
import org.codehaus.groovy.grails.plugins.riak.domain.RiakDomainClassArtefactHandler
import org.springframework.core.io.Resource

class GormRiakGrailsPlugin {
    // the plugin version
    def version = "0.1"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.3.3 > *"
    // the other plugins this plugin depends on
    def dependsOn = [core: '1.3.3 > *',
             hibernate: '1.2 > *'
    ]
    
    def loadAfter = ['core', 'domainClass', 'hibernate']
    
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp",
            "grails-app/domain/**/*",
            "test/**/*",
            "web-app/**"
    ]

    def artefacts = [RiakDomainClassArtefactHandler]

    // TODO Fill in these fields
    def author = "Akhil Kodali"
    def authorEmail = ""
    def title = "Grials Riak Plugin"
    def description = "A plugin that emulates the behavior of the GORM-Hibernate plugin against a Riak database"

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/gorm-riak"

    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional), this event occurs before 
    }

    def doWithSpring = RiakPluginSupport.doWithSpring

    def doWithDynamicMethods = RiakPluginSupport.doWithDynamicMethods
/*
//    def doWithSpring = {
//        // TODO Implement runtime spring config (optional)
//    }

//    def doWithDynamicMethods = { ctx ->
//        // TODO Implement registering dynamic methods to classes (optional)
//    }

    def doWithApplicationContext = { applicationContext ->
        // TODO Implement post initialization spring config (optional)
    }

    def onChange = { event ->
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.
    }

    def onConfigChange = { event ->
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    } */
}
