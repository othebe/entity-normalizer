package annotations.processor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Map;
import java.util.Set;

public interface ITemplateGenerator {
    boolean canProcess(TypeElement typeElement);
    void process(Set<? extends Element> elements, ProcessingEnvironment processingEnv);
    Map<String, Template> getTemplates();
}
