package annotations.processor.templategenerators;

import annotations.EntitySpec;
import annotations.EntityId;
import annotations.processor.ITemplateGenerator;
import annotations.processor.Template;
import com.squareup.javapoet.*;
import core.IEntity;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import java.util.*;

/**
 * Generates Entity templates from EntitySpec annotated files.
 */
public class EntityTemplateGenerator implements ITemplateGenerator {
    private final Map<String, Template> templates;

    private final Set<TypeName> entityClasses;
    private final Map<String, TypeName> typeNameByGeneratedClassName;

    public EntityTemplateGenerator() {
        this.templates = new HashMap<>();
        this.entityClasses = new HashSet<>();
        this.typeNameByGeneratedClassName = new HashMap<>();
    }

    @Override
    public boolean canProcess(TypeElement typeElement) {
        return typeElement.getQualifiedName().toString().equals(EntitySpec.class.getCanonicalName());
    }

    @Override
    public void process(Set<? extends Element> entitySpecs, ProcessingEnvironment processingEnv) {
        for (Element entitySpec : entitySpecs) {
            ClassName entityType = Utils.getEntityType(entitySpec, processingEnv);

            typeNameByGeneratedClassName.put(entityType.simpleName(), entityType);
            entityClasses.add(entityType);
        }

        for (Element entity : entitySpecs) {
            process(entity, processingEnv);
        }
    }

    @Override
    public Map<String, Template> getTemplates() {
        return templates;
    }

    public void process(Element entitySpec, ProcessingEnvironment processingEnv) {
        ClassName entityType = Utils.getEntityType(entitySpec, processingEnv);
        String fullyQualifiedName = String.format("%s.%s", entityType.packageName(), entityType.simpleName());

        Template template = new Template(entityType.packageName(), entityType.simpleName());

        // Add property fields and getters.
        List<FieldSpec> fieldSpecs = new LinkedList<>();
        FieldSpec idFieldSpec = null;
        for (Element enclosedElement : entitySpec.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.FIELD) {
                FieldSpec fieldSpec = getFieldSpecFor(enclosedElement);

                EntityId entityId = enclosedElement.getAnnotation(EntityId.class);
                if (entityId != null) {
                    if (idFieldSpec == null) {
                        idFieldSpec = fieldSpec;
                    } else {
                        throw new RuntimeException("Error processing EntitySpec: An ID has already been defined in " + entitySpec.getSimpleName().toString());
                    }
                }

                template.add(fieldSpec);

                MethodSpec fieldGetterSpec = getGetterFor(fieldSpec, processingEnv.getLocale());
                template.add(fieldGetterSpec);

                fieldSpecs.add(fieldSpec);
            }
        }

        if (idFieldSpec == null) {
            throw new RuntimeException("Error processing EntitySpec: No ID defined in " + entitySpec.getSimpleName().toString());
        }

        // Constructor.
        template.add(getConstructor(fieldSpecs));

        // Interface IEntity.
        template.add(ParameterizedTypeName.get(
                ClassName.get(IEntity.class),
                Utils.getSafelyBoxedTypeName(idFieldSpec.type)));
        // id()
        template.add(getMethodSpec_id(idFieldSpec));
        // entityType()
        template.add(getMethodSpec_entityType(fullyQualifiedName));

        templates.put(fullyQualifiedName, template);
    }

    /**
     * Generates a constructor based on property fields.
     * @param fieldSpecs Specs for property fields.
     * @return Constructor methodSpec.
     */
    private MethodSpec getConstructor(List<FieldSpec> fieldSpecs) {
        MethodSpec.Builder builder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);
        for (FieldSpec fieldSpec : fieldSpecs) {
            ParameterSpec parameterSpec = ParameterSpec.builder(fieldSpec.type, fieldSpec.name).build();
            builder.addParameter(parameterSpec);
            builder.addStatement("this.$N = $N", fieldSpec, parameterSpec);
        }

        return builder.build();
    }

    /**
     * Generates property fields for an element.
     * @param element Element to generate property field for.
     * @return FieldSpec for element.
     */
    private FieldSpec getFieldSpecFor(Element element) {
        TypeName elementType = TypeName.get(element.asType());

        if (elementType instanceof ParameterizedTypeName) {
            elementType = Utils.getParameterizedTypeWithGeneratedTypes((ParameterizedTypeName) elementType, typeNameByGeneratedClassName);
        } else if (typeNameByGeneratedClassName.containsKey(elementType.toString())) {
            elementType = typeNameByGeneratedClassName.get(elementType.toString());
        }

        return FieldSpec.builder(elementType, element.getSimpleName().toString(), Modifier.PRIVATE)
                .build();
    }

    /**
     * Generates getter for a property field.
     * @param fieldSpec FieldSpec for a property.
     * @param locale Locale.
     * @return Getter methodSpec for a property.
     */
    private MethodSpec getGetterFor(FieldSpec fieldSpec, Locale locale) {
        String elementName = fieldSpec.name;
        String methodName = String.format("get%s", Utils.convertToPascalCase(elementName, locale));

        return MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(fieldSpec.type)
                .addStatement("return $N", fieldSpec).build();
    }

    /**
     * Generates a getter for the id field.
     * @param idFieldSpec ID field.
     * @return id() methodSpec.
     */
    private MethodSpec getMethodSpec_id(FieldSpec idFieldSpec) {
        return MethodSpec.methodBuilder("id")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(Utils.getSafelyBoxedTypeName(idFieldSpec.type))
                .addStatement("return $N", idFieldSpec)
                .build();
    }

    /**
     * Generates a getter for the entity type field.
     * @param fullyQualifiedName Fully qualified name for this Entity.
     * @return entityType() methodSpec.
     */
    private MethodSpec getMethodSpec_entityType(String fullyQualifiedName) {
        return MethodSpec.methodBuilder("entityType")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return $S", fullyQualifiedName)
                .build();
    }
}
