package grails.plugins.riak;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Akhil Kodali
 */
@Target ({ElementType.TYPE})
@Retention (RetentionPolicy.RUNTIME)
@Documented
@GroovyASTTransformationClass ({"org.codehaus.groovy.grails.plugins.riak.ast.RiakEntityASTTransformation"})
public @interface RiakEntity {

    String typeFieldName() default "type";

    String type();

}

