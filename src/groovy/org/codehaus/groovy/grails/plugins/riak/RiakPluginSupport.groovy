package org.codehaus.groovy.grails.plugins.riak

import org.apache.commons.lang.StringUtils
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.codehaus.groovy.grails.plugins.DomainClassPluginSupport
import org.codehaus.groovy.grails.plugins.riak.domain.RiakDomainClass
import org.codehaus.groovy.grails.plugins.riak.domain.RiakDomainClassArtefactHandler
// import org.codehaus.groovy.grails.plugins.riak.json.JsonConverterUtils
// import org.codehaus.groovy.grails.plugins.riak.json.JsonDateConverter
import org.codehaus.groovy.grails.support.SoftThreadLocalMap
import org.codehaus.groovy.grails.validation.GrailsDomainClassValidator
import org.codehaus.groovy.grails.web.binding.DataBindingLazyMetaPropertyMap
import org.codehaus.groovy.grails.web.binding.DataBindingUtils
import org.springframework.beans.BeanUtils
import org.springframework.beans.factory.config.MethodInvokingFactoryBean
import org.springframework.context.ApplicationContext
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import grails.converters.JSON
import com.basho.riak.client.RiakClient
import com.basho.riak.client.RiakObject


/**
 *
 * @author Akhil Kodali
 */
public class RiakPluginSupport {

    static final PROPERTY_INSTANCE_MAP = new SoftThreadLocalMap()

    static def doWithSpring = {ApplicationContext ctx ->

/*
        // extend the jriak ValueRow class to automatically look up properties of the internal value object
        ValueRow.metaClass.propertyMissing = {String name ->

			// only look if the value is a Map; which should happen unless the view
			// emit doesn't contain a named parameters, e.g. emit(doc._id, 1)
			if (delegate.value instanceof Map) {
				Map map = delegate.value

				if (!map.containsKey(name)) {
					throw new MissingPropertyException(name)
				}

				// look for a property of the same name in our domain class
				Object value = map.get(name)
				if (value) {
					Class type = map.get('__domainClass')?.getPropertyByName(name)?.type
					if (type && !(type instanceof String) && !value.getClass().isAssignableFrom(type)) {
						value = JsonConverterUtils.fromJSON(type, value)
						map.put(name, value)
					}
				}

				return value

			} else {
				throw new MissingPropertyException(name)
			}
        }
*/
        // register our RiakDomainClass artefacts that weren't already picked up by grails
        application.domainClasses.each {GrailsDomainClass dc ->
            if (RiakDomainClassArtefactHandler.isRiakDomainClass(dc.clazz)) {
                RiakDomainClass riakDomainClass = new RiakDomainClass(dc.clazz)
                application.addArtefact(RiakDomainClassArtefactHandler.TYPE, riakDomainClass)
            }
        }

        application.RiakDomainClasses.each {RiakDomainClass dc ->

            // Note the use of Groovy's ability to use dynamic strings in method names!
            "${dc.fullName}"(dc.getClazz()) {bean ->
                bean.singleton = false
                bean.autowire = "byName"
            }

            "${dc.fullName}DomainClass"(MethodInvokingFactoryBean) {
                targetObject = ref("grailsApplication", true)
                targetMethod = "getArtefact"
                arguments = [RiakDomainClassArtefactHandler.TYPE, dc.fullName]
            }

            "${dc.fullName}PersistentClass"(MethodInvokingFactoryBean) {
                targetObject = ref("${dc.fullName}DomainClass")
                targetMethod = "getClazz"
            }

            "${dc.fullName}Validator"(GrailsDomainClassValidator) {
                messageSource = ref("messageSource")
                domainClass = ref("${dc.fullName}DomainClass")
                grailsApplication = ref("grailsApplication", true)
            }
        }
    }

    static def doWithDynamicMethods = {ApplicationContext ctx ->
        enhanceDomainClasses(application, ctx)
    }

    static enhanceDomainClasses(GrailsApplication application, ApplicationContext ctx) {

        application.RiakDomainClasses.each {RiakDomainClass domainClass ->
            RiakClient db = getRiakDatabase(application)

            addInstanceMethods(application, domainClass, ctx, db)
            addStaticMethods(application, domainClass, ctx, db)
            addDynamicFinderSupport(application, domainClass, ctx, db)

            addValidationMethods(application, domainClass, ctx)

        }
    }

    private static addInstanceMethods(GrailsApplication application, RiakDomainClass dc, ApplicationContext ctx, RiakClient db) {
        def metaClass = dc.metaClass
        def domainClass = dc
        def riak = db

        metaClass.save = {->
            save(null)
        }

        metaClass.save = {Map args = [:] ->

            // todo: add support for failOnError:true in grails 1.2 (GRAILS-4343)
            if (validate()) {
                def object = autoTimeStamp(application, delegate)
                println dc.inspect()
                println object.inspect()
				String bucket = "test"
				String key = getDocumentId(dc, object)
				String value = (object as JSON).toString()
				println bucket
				println key
				println value
				def newo = new RiakObject(bucket, key, value.getBytes())
                try {
					def oo = riak.store( newo )
					println oo.inspect()
                } catch (Exception e) {
					e.printStackTrace()
				}
                return object
            }

            return null
        }

        metaClass.delete = {->
            delete(null)
        }

        metaClass.delete = {Map args = [:] ->
            riak.delete getDocumentId(domainClass, delegate), getDocumentVersion(domainClass, delegate)
        }
        
        metaClass.toJSON = {->
            return db.jsonConfig.getJsonGenerator().forValue(delegate);
        }
    }

    private static addStaticMethods(GrailsApplication application, RiakDomainClass dc, ApplicationContext ctx, RiakClient db) {
        def metaClass = dc.metaClass
        def domainClass = dc
        def riak = db

        metaClass.static.get = {Serializable docId ->
            def riak_object = riak.fetch("test", docId.toString()).getObject()
            if (riak_object == null) return null
            return domainClass.clazz.newInstance(JSON.parse(riak_object.getValue()))
        }

        // Foo.exists(1)
        metaClass.static.exists = {Serializable docId ->
            get(docId) != null
        }

        metaClass.static.delete = {Serializable docId, String version ->
            riak.delete docId.toString(), version
        }

        metaClass.static.bulkSave = {List documents ->
            return bulkSave(documents, false)
        }

        metaClass.static.bulkSave = {List documents, Boolean allOrNothing ->
            documents.each {doc ->
                autoTimeStamp(application, doc)
            }

            return riak.bulkCreateDocuments(documents, allOrNothing)
        }

        metaClass.static.bulkDelete = {List documents ->
            return bulkDelete(documents, false)
        }

        metaClass.static.bulkDelete = {List documents, boolean allOrNothing ->
            return riak.bulkDeleteDocuments(documents, allOrNothing)
        }
        
        metaClass.static.findAll = {Map o = [:] ->
            return null
        }

        metaClass.static.count = {Map o = [:] ->
            return count(null, o)
        }

        metaClass.static.count = {String viewName, Map o = [:] ->
			def r = riak.listBucket("test")
			if (r.isSuccess()) {
				return r.getBucketInfo().getKeys().size()
			}
			return 0
        }

        metaClass.static.list = {Map o = [:] ->
            return list(null, o)
        }

        metaClass.static.list = {String viewName, Map o = [:] ->
            def view = viewName
            def r = riak.listBucket("test")
			if (r.isSuccess()) {
				def info = r.getBucketInfo()
				return info.getKeys().collect { key ->
					println key
					get(key)
				}
			}
            return []
        }

    }

    private static addDynamicFinderSupport(GrailsApplication application, RiakDomainClass dc, ApplicationContext ctx, RiakClient riak) {
        def metaClass = dc.metaClass
        def domainClass = dc
        
        // This adds basic dynamic finder support.
        metaClass.static.methodMissing = {String methodName, args ->

            // find methods (can have search keys)
            def matcher = (methodName =~ /^(find)(\w+)$/)
            if (!matcher.matches()) {

                // list methods (just contains options)
                matcher = (methodName =~ /^(list)(\w+)$/)
                matcher.reset()
                if (!matcher.matches()) {

                    // count methods (only options)
                    matcher = (methodName =~ /^(count)(\w+)$/)
                    matcher.reset()
                    if (!matcher.matches()) {
                        throw new MissingMethodException(methodName, delegate, args, true)
                    }
                }
            }

            // set the view to everything after the method type (change first char to lowerCase).
            def method = matcher.group(1)
            def view = matcher.group(2)
            view = domainClass.designName + "/" + view.substring(0, 1).toLowerCase() + view.substring(1)

            // options should be the last map argument.
            args = args.toList()
            def options = (args.size() > 0 && args[args.size() - 1] instanceof Map) ? args.remove(args.size() - 1) : [:]

            // call the appropriate query and return the results
            if (method == "find") {

                // assume that the list of keys (if any) is everything else
                def keys = (args ?: [])
                if (keys) {
                    return queryViewByKeys(view, keys, options);
                } else {
                    return queryView(view, options)
                }

            } else if (method == "list") {
                return queryView(view, options)

            } else {
                return count(view, options)

            }
        }
    }

    private static addValidationMethods(GrailsApplication application, RiakDomainClass dc, ApplicationContext ctx) {
        def metaClass = dc.metaClass
        def domainClass = dc

        metaClass.static.getConstraints = {->
            domainClass.constrainedProperties
        }

        metaClass.getConstraints = {->
            domainClass.constrainedProperties
        }

        metaClass.constructor = {Map map ->
            def instance = ctx.containsBean(domainClass.fullName) ? ctx.getBean(domainClass.fullName) : BeanUtils.instantiateClass(domainClass.clazz)
            DataBindingUtils.bindObjectToDomainInstance(domainClass, instance, map)
            DataBindingUtils.assignBidirectionalAssociations(instance, map, domainClass)
            return instance
        }
        metaClass.setProperties = {Object o ->
            DataBindingUtils.bindObjectToDomainInstance(domainClass, delegate, o)
        }
        metaClass.getProperties = {->
            new DataBindingLazyMetaPropertyMap(delegate)
        }

        metaClass.hasErrors = {-> delegate.errors?.hasErrors() }

        def get
        def put
        try {
            def rch = application.classLoader.loadClass("org.springframework.web.context.request.RequestContextHolder")
            get = {
                def attributes = rch.getRequestAttributes()
                if (attributes) {
                    return attributes.request.getAttribute(it)
                } else {
                    return PROPERTY_INSTANCE_MAP.get().get(it)
                }
            }
            put = {key, val ->
                def attributes = rch.getRequestAttributes()
                if (attributes) {
                    attributes.request.setAttribute(key, val)
                } else {
                    PROPERTY_INSTANCE_MAP.get().put(key, val)
                }
            }
        } catch (Throwable e) {
            get = { PROPERTY_INSTANCE_MAP.get().get(it) }
            put = {key, val -> PROPERTY_INSTANCE_MAP.get().put(key, val) }
        }

        metaClass.getErrors = {->
            def errors
            def key = "org.codehaus.groovy.grails.ERRORS_${delegate.class.name}_${System.identityHashCode(delegate)}"
            errors = get(key)
            if (!errors) {
                errors = new BeanPropertyBindingResult(delegate, delegate.getClass().getName())
                put key, errors
            }
            errors
        }

        metaClass.setErrors = {Errors errors ->
            def key = "org.codehaus.groovy.grails.ERRORS_${delegate.class.name}_${System.identityHashCode(delegate)}"
            put key, errors
        }

        metaClass.clearErrors = {->
            delegate.setErrors(new BeanPropertyBindingResult(delegate, delegate.getClass().getName()))
        }

        if (!metaClass.respondsTo(dc.getReference(), "validate")) {
            metaClass.validate = {->
                DomainClassPluginSupport.validateInstance(delegate, ctx)
            }
        }
    }

    private static Object autoTimeStamp(GrailsApplication application, Object domain) {

        RiakDomainClass dc = (RiakDomainClass) application.getArtefact(RiakDomainClassArtefactHandler.TYPE, domain.getClass().getName())
        if (dc) {
            def metaClass = dc.metaClass

            MetaProperty property = metaClass.hasProperty(dc, GrailsDomainClassProperty.DATE_CREATED)
            def time = System.currentTimeMillis()
            if (property && domain[property.name] == null && getDocumentVersion(dc, domain) == null) {
                def now = property.getType().newInstance([time] as Object[])
                domain[property.name] = now
            }

            property = metaClass.hasProperty(dc, GrailsDomainClassProperty.LAST_UPDATED)
            if (property) {
                def now = property.getType().newInstance([time] as Object[])
                domain[property.name] = now
            }
        }

        return domain
    }

    private static String getDocumentId(RiakDomainClass dc, Object domain) {
        def id = dc.getIdentifier()
        if (id) {
            domain[id.name]
        }
    }

    private static String getDocumentVersion(RiakDomainClass dc, Object domain) {
        def version = dc.getVersion()
        if (version) {
            domain[version.name]
        }
    }

    private static RiakClient getRiakDatabase(GrailsApplication application) {
        def ds = application.config.riak

        String host = ds?.host ?: "localhost"
        Integer port = ds?.port ?: 8098
        System.err << host
        String database = ds?.database ?: application.metadata["app.name"]
        def riak = new RiakClient("http://${host}:${port}/${database}")        
        return riak
    }


    private static List convertKeys(List keys) {
        def values = []
        keys.each {key ->
            values << JsonConverterUtils.toJSON(key)
        }

        return values
    }
}
