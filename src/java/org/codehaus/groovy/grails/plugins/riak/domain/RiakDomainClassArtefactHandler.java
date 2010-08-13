package org.codehaus.groovy.grails.plugins.riak.domain;

import org.codehaus.groovy.grails.commons.ArtefactHandlerAdapter;
import org.codehaus.groovy.grails.commons.GrailsClass;

import grails.plugins.riak.RiakEntity;

/**
 * @author Akhil Kodali
 */
public class RiakDomainClassArtefactHandler extends ArtefactHandlerAdapter {

    public static final String TYPE = "RiakDomain";

    public RiakDomainClassArtefactHandler() {
        super(TYPE, RiakDomainClass.class, RiakDomainClass.class, null);
    }

    public boolean isArtefactClass(Class clazz) {
        return isRiakDomainClass(clazz);
    }

    public static boolean isRiakDomainClass(Class clazz) {
        return clazz != null && clazz.getAnnotation(RiakEntity.class) != null;
    }

    public GrailsClass newArtefactClass(Class artefactClass) {
        return new RiakDomainClass(artefactClass);
    }
}

