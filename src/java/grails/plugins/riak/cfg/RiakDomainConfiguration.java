package grails.plugins.riak.cfg;

import org.codehaus.groovy.grails.commons.GrailsDomainClass;
import org.codehaus.groovy.grails.orm.hibernate.cfg.DefaultGrailsDomainConfiguration;
import org.codehaus.groovy.grails.orm.hibernate.cfg.GrailsDomainConfiguration;
import org.codehaus.groovy.grails.plugins.riak.domain.RiakDomainClassArtefactHandler;

/**
 * @author Akhil Kodali
 */
public class RiakDomainConfiguration extends DefaultGrailsDomainConfiguration {

    private static final long serialVersionUID = 6586536745135709599L;

    @Override
    public GrailsDomainConfiguration addDomainClass(GrailsDomainClass domainClass) {
        if (!RiakDomainClassArtefactHandler.isRiakDomainClass(domainClass.getClazz())) {
            return super.addDomainClass(domainClass);
        }

        return this;
    }
}

