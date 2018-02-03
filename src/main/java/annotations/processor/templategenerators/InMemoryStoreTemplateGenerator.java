package annotations.processor.templategenerators;

import annotations.EntitySpec;
import annotations.processor.ITemplateGenerator;
import annotations.processor.Template;
import com.squareup.javapoet.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import java.util.*;

/**
 * Generates a in-memory store template from EntitySpec annotated files.
 */
public class InMemoryStoreTemplateGenerator implements ITemplateGenerator {
    private final String PACKAGE = "entitynormalizer.store";
    private final String CLASSNAME = "InMemoryEntityStore";

    private final Map<String, Template> templates;

    public InMemoryStoreTemplateGenerator() {
        this.templates = new HashMap<>();
    }

    @Override
    public boolean canProcess(TypeElement typeElement) {
        return typeElement.getQualifiedName().toString().equals(EntitySpec.class.getCanonicalName());
    }

    @Override
    public void process(Set<? extends Element> entitySpecs, ProcessingEnvironment processingEnv) {
        Template template = new Template(PACKAGE, CLASSNAME);

        // Generate property fields for every Entity.
        Map<TypeName, FieldSpec> entityFieldSpecs = new HashMap<>();
        for (Element entitySpec : entitySpecs) {
            FieldSpec fieldSpec = getEntityField(entitySpec, processingEnv);
            template.add(fieldSpec);
            entityFieldSpecs.put(Utils.getEntityType(entitySpec, processingEnv), fieldSpec);
        }

        // Generate getters and setters for every Entity.
        for (Element entitySpec : entitySpecs) {
            template.add(getPutterForEntity(entitySpec, entityFieldSpecs, processingEnv));
            template.add(getGetterForEntity(entitySpec, entityFieldSpecs, processingEnv));
        }

        // Add reader and writer interfaces.
        template.add(ClassName.get(StoreReaderInterfaceTemplateGenerator.PACKAGE, StoreReaderInterfaceTemplateGenerator.CLASSNAME));
        template.add(ClassName.get(StoreWriterInterfaceTemplateGenerator.PACKAGE, StoreWriterInterfaceTemplateGenerator.CLASSNAME));

        templates.put(CLASSNAME, template);
    }

    @Override
    public Map<String, Template> getTemplates() {
        return templates;
    }

    /**
     * Generates a property field mapping an Entity by ID.
     * @param entitySpecElement EntitySpec annotated element.
     * @param processingEnv Processing environment.
     * @return Map of ID to Entity for the Entity specified in the Entity spec.
     */
    private FieldSpec getEntityField(Element entitySpecElement, ProcessingEnvironment processingEnv) {
        ClassName entityType = Utils.getEntityType(entitySpecElement, processingEnv);

        ParameterizedTypeName Map_Id_Entity = ParameterizedTypeName.get(
                ClassName.get(HashMap.class),
                Utils.getIdTypeName(entitySpecElement),
                entityType);

        String fieldName = String.format("%sById", Utils.convertToCamelCase(entityType.simpleName(), processingEnv.getLocale()));

        return FieldSpec.builder(Map_Id_Entity, fieldName, Modifier.PRIVATE)
                .initializer("new $T()", Map_Id_Entity)
                .build();
    }

    /**
     * Generates a putter method for an Entity that returns the success of the write operation..
     * @param entitySpecElement EntitySpec annotated element.
     * @param entityFieldSpecs Map of Entity (map) fieldSpecs by Entity types.
     * @param processingEnv Processing environment.
     * @return put(Entity) -> True if succeeded, else false.
     */
    private MethodSpec getPutterForEntity(Element entitySpecElement, Map<TypeName, FieldSpec> entityFieldSpecs, ProcessingEnvironment processingEnv) {
        TypeName entityType = Utils.getEntityType(entitySpecElement, processingEnv);

        ParameterSpec entity = ParameterSpec.builder(entityType, "entity").build();

        MethodSpec.Builder builder = MethodSpec.methodBuilder("put")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(entity)
                .returns(TypeName.BOOLEAN);

        // Add current entity to the store.
        builder.addStatement("$N.put($N.id(), $N)", entityFieldSpecs.get(entityType), entity, entity);

        builder.addStatement("return true");

        return builder.build();
    }

    /**
     * Generates a getter method for an Entity.
     * @param entitySpecElement EntitySpec annotated element.
     * @param entityFieldSpecs Map of Entity (map) fieldSpecs by Entity types.
     * @param processingEnv Processing environment.
     * @return getEntity(ID) -> Entity methodSpec.
     */
    private MethodSpec getGetterForEntity(Element entitySpecElement, Map<TypeName, FieldSpec> entityFieldSpecs, ProcessingEnvironment processingEnv) {
        ClassName entityType = Utils.getEntityType(entitySpecElement, processingEnv);

        ParameterSpec id = ParameterSpec.builder(Utils.getIdTypeName(entitySpecElement), "id").build();

        MethodSpec.Builder builder = MethodSpec.methodBuilder(String.format("get%s", entityType.simpleName()))
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(id)
                .returns(entityType);

        builder.addStatement("return $N.get($N)", entityFieldSpecs.get(entityType), id);

        return builder.build();
    }
}
