package annotations.processor.templategenerators;

import annotations.EntitySpec;
import annotations.processor.ITemplateGenerator;
import annotations.processor.Template;
import com.squareup.javapoet.*;
import core.IEntity;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/**
 * Generates an interface for writing to the repository.
 */
public class RepositoryWriterInterfaceTemplateGenerator implements ITemplateGenerator {
    public static final String PACKAGE = "entitynormalizer.store";
    public static final String CLASSNAME = "INormalizedEntityRepositoryWriter";

    private final Map<String, Template> templates;

    public RepositoryWriterInterfaceTemplateGenerator() {
        this.templates = new HashMap<>();
    }

    @Override
    public boolean canProcess(TypeElement typeElement) {
        return typeElement.getQualifiedName().toString().equals(EntitySpec.class.getCanonicalName());
    }

    @Override
    public void process(Set<? extends Element> entitySpecs, ProcessingEnvironment processingEnv) {
        Template template = new Template(PACKAGE, CLASSNAME, true);

        // Generate getters and setters for every Entity.
        for (Element entitySpec : entitySpecs) {
            template.add(getWriterForEntity(entitySpec, processingEnv));
        }

        templates.put(CLASSNAME, template);
    }

    @Override
    public Map<String, Template> getTemplates() {
        return templates;
    }

    private MethodSpec getWriterForEntity(Element entitySpecElement, ProcessingEnvironment processingEnv) {
        TypeName entityType = Utils.getEntityType(entitySpecElement, processingEnv);

        ParameterizedTypeName Set_Entity = ParameterizedTypeName.get(Set.class, IEntity.class);

        ParameterSpec entity = ParameterSpec.builder(entityType, "entity").build();

        MethodSpec.Builder builder = MethodSpec.methodBuilder("put")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addParameter(entity)
                .returns(Set_Entity);

        return builder.build();
    }
}
