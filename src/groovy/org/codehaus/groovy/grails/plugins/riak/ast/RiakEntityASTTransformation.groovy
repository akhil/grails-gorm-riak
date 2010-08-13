package org.codehaus.groovy.grails.plugins.riak.ast

import grails.plugins.riak.RiakEntity
import grails.plugins.riak.RiakId
import grails.plugins.riak.RiakVersion
import java.lang.reflect.Modifier
import javax.persistence.Id
import javax.persistence.Transient
import javax.persistence.Version
import org.apache.commons.lang.StringUtils
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.FieldExpression
import org.codehaus.groovy.ast.expr.GStringExpression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.classgen.Verifier
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.codehaus.groovy.syntax.Token
import org.codehaus.groovy.syntax.Types
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import grails.converters.JSON
/**
 *
 * @author Akhil Kodali
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class RiakEntityASTTransformation implements ASTTransformation {

    private static final Log log = LogFactory.getLog(RiakEntityASTTransformation.class)

    private static final String IDENTITY = GrailsDomainClassProperty.IDENTITY
    private static final String VERSION = GrailsDomainClassProperty.VERSION

    private static final ClassNode RIAK_ENTITY = new ClassNode(RiakEntity)

    private static final ClassNode RIAK_ID = new ClassNode(RiakId)
    private static final ClassNode RIAK_VERSION = new ClassNode(RiakVersion)
    
    private static final ClassNode PERSISTENCE_ID = new ClassNode(Id)
    private static final ClassNode PERSISTENCE_VERSION = new ClassNode(Version)
    private static final ClassNode PERSISTENCE_TRANSIENT = new ClassNode(Transient)

    private static final ClassNode STRING_TYPE = new ClassNode(String)
    private static final ClassNode BOOLEAN_TYPE = new ClassNode(boolean)


    public void visit(ASTNode[] nodes, SourceUnit sourceUnit) {
        if (nodes.length != 2 || !(nodes[0] instanceof AnnotationNode) || !(nodes[1] instanceof AnnotatedNode)) {
            throw new RuntimeException("Internal error: expecting [AnnotationNode, AnnotatedNode] but got: " + Arrays.asList(nodes))
        }

        AnnotationNode node = (AnnotationNode) nodes[0]
        ClassNode owner = (ClassNode) nodes[1]

        injectEntityType(owner, node)

        injectIdProperty(owner)
        injectVersionProperty(owner)       
    }

    private void injectEntityType(ClassNode classNode, AnnotationNode entity) {

        String typeValue = entity.members["type"]?.value ?: classNode.nameWithoutPackage.toLowerCase()
        String typeFieldName = entity.members["typeFieldName"]?.value ?: "type"

        // inject the type property if it doesn't already exist
        if (typeValue != "" && typeFieldName != "" && !getProperty(classNode, typeFieldName)) {

            String getterName = "get" + Verifier.capitalize(typeFieldName)
            MethodNode getter = classNode.getGetterMethod(getterName)
            if (getter == null) {
                getter = new MethodNode(getterName,
                        Modifier.PUBLIC,
                        STRING_TYPE,
                        Parameter.EMPTY_ARRAY,
                        null,
                        new ReturnStatement(
                                new ConstantExpression(typeValue)
                        ))

                classNode.addMethod(getter)
            }
        }
    }

    private void injectIdProperty(ClassNode classNode) {
        Collection<FieldNode> nodes = classNode.fields.findAll {FieldNode fieldNode -> fieldNode.getAnnotations(RIAK_ID) || fieldNode.getAnnotations(PERSISTENCE_ID) }
        PropertyNode identity = getProperty(classNode, IDENTITY)

        if (nodes) {
            // look to see if the identity field was injected and isn't one of our annotated field(s)
            if (identity && identity.field.lineNumber < 0 && !nodes.findAll {FieldNode fieldNode -> fieldNode.name == identity.name}) {

                // change the injected toString() method to use the proper id field
                fixupToStringMethod(classNode, nodes[0])

                // remove the old identifier
                removeProperty(classNode, identity.name)
            }
        } else {

            // if we don't have an annotated id then look for a plain id field
            if (identity) {
                if (identity.type.typeClass != String.class && identity.field.lineNumber < 0) {
                    if (log.isDebugEnabled()) {
                        log.debug("Changing the type of property [" + IDENTITY + "] of class [" + classNode.getName() + "] to String.")
                    }
                    identity.field.type = STRING_TYPE
                }
            } else {

                if (log.isDebugEnabled()) {
                    log.debug("Adding property [" + IDENTITY + "] to class [" + classNode.getName() + "]")
                }
                identity = classNode.addProperty(IDENTITY, Modifier.PUBLIC, STRING_TYPE, null, null, null)
            }

            nodes = [identity.field]
        }
    }

    private void fixupToStringMethod(ClassNode classNode, FieldNode idNode) {

        MethodNode method = classNode.getDeclaredMethod("toString", [] as Parameter[]);
        if (method != null && method.lineNumber < 0 && (method.isPublic() || method.isProtected()) && !method.isAbstract()) {
            GStringExpression ge = new GStringExpression(classNode.getName() + ' : ${' + idNode.name + '}');
            ge.addString(new ConstantExpression(classNode.getName() + " : "));
            ge.addValue(new VariableExpression(idNode.name));

            method.variableScope.removeReferencedClassVariable("id")
            method.variableScope.putReferencedClassVariable(idNode)

            method.code = new ReturnStatement(ge);

            if (log.isDebugEnabled()) {
                log.debug("Changing method [toString()] on class [" + classNode.getName() + "] to use id field [" + id + "]");
            }
        }
    }

    private void injectVersionProperty(ClassNode classNode) {
        Collection<FieldNode> nodes = classNode.fields.findAll {FieldNode fieldNode -> fieldNode.getAnnotations(RIAK_VERSION) || fieldNode.getAnnotations(PERSISTENCE_VERSION) }
        PropertyNode version = getProperty(classNode, VERSION)

        if (nodes) {
            // look to see if the version field was injected and isn't one of our annotated field(s)
            if (version && version.field.lineNumber < 0 && !nodes.findAll {FieldNode fieldNode -> fieldNode.name == version.name}) {

                // remove the old version
                removeProperty(classNode, version.name)
            }

        } else {

            // if we don't have an annotated version then look for a plain version field
            if (version) {
                if (version.type.typeClass != String.class && version.field.lineNumber < 0) {
                    if (log.isDebugEnabled()) {
                        log.debug("Changing the type of property [" + VERSION + "] of class [" + classNode.getName() + "] to String.")
                    }
                    version.field.type = STRING_TYPE
                }
            } else {

                if (log.isDebugEnabled()) {
                    log.debug("Adding property [" + VERSION + "] to class [" + classNode.getName() + "]")
                }
                version = classNode.addProperty(VERSION, Modifier.PUBLIC, STRING_TYPE, null, null, null)
            }

            nodes = [version.field]
        }
    }

    private PropertyNode getProperty(ClassNode classNode, String propertyName) {
        if (classNode == null || StringUtils.isBlank(propertyName))
            return null

        // find the given class property
        // do we need to deal with parent classes???
        for (PropertyNode pn: classNode.properties) {
            if (pn.getName().equals(propertyName) && !pn.isPrivate()) {
                return pn
            }
        }

        return null
    }

    private removeProperty(ClassNode classNode, String propertyName) {
        if (log.isDebugEnabled()) {
            log.debug("Removing property [" + propertyName + "] from class [" + classNode.getName() + "]")
        }

        // remove the property from the fields and properties arrays
        for (int i = 0; i < classNode.fields.size(); i++) {
            if (classNode.fields[i].name == propertyName) {
                classNode.fields.remove(i)
                break
            }
        }
        for (int i = 0; i < classNode.properties.size(); i++) {
            if (classNode.properties[i].name == propertyName) {
                classNode.properties.remove(i)
                break
            }
        }

        // this doesn't seem to be necessary (and is only technically possible
        // because groovy ignores private scope), but we're trying to be thorough.
        classNode.fieldIndex.remove(propertyName)
    }
}

