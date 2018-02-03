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

public class StoreWriterInterfaceTemplateGenerator implements ITemplateGenerator {
    public static final String PACKAGE = "entitynormalizer.store";
    public static final String CLASSNAME = "IEntityStoreWriter";

    private final Map<String, Template> templates;

    public StoreWriterInterfaceTemplateGenerator() {
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
            template.add(getGetterForEntity(entitySpec, processingEnv));
        }

        templates.put(CLASSNAME, template);
    }

    @Override
    public Map<String, Template> getTemplates() {
        return templates;
    }

    private MethodSpec getGetterForEntity(Element entitySpecElement, ProcessingEnvironment processingEnv) {
        TypeName entityType = Utils.getEntityType(entitySpecElement, processingEnv);

        ParameterSpec entity = ParameterSpec.builder(entityType, "entity").build();

        MethodSpec.Builder builder = MethodSpec.methodBuilder("put")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addParameter(entity)
                .returns(TypeName.BOOLEAN);

        return builder.build();
    }
}
