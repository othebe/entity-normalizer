package annotations.processor.templategenerators;

import annotations.EntitySpec;
import annotations.processor.ITemplateGenerator;
import annotations.processor.Template;
import com.squareup.javapoet.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/**
 * Generates an interface for reading from the repository.
 */
public class RepositoryReaderInterfaceTemplateGenerator implements ITemplateGenerator {
    public static final String PACKAGE = "entitynormalizer.store";
    public static final String CLASSNAME = "INormalizedEntityRepositoryReader";

    private final Map<String, Template> templates;

    public RepositoryReaderInterfaceTemplateGenerator() {
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
        ClassName entityType = Utils.getEntityType(entitySpecElement, processingEnv);

        ParameterSpec id = ParameterSpec.builder(Utils.getIdTypeName(entitySpecElement), "id").build();

        MethodSpec.Builder builder = MethodSpec.methodBuilder(String.format("get%s", entityType.simpleName()))
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addParameter(id)
                .returns(entityType);

        return builder.build();
    }
}
