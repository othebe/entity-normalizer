package annotations.processor;

import annotations.EntitySpec;
import annotations.processor.templategenerators.EntityTemplateGenerator;
import annotations.processor.templategenerators.NormalizedStoreTemplateGenerator;
import com.google.auto.service.AutoService;
import com.google.auto.service.processor.AutoServiceProcessor;
import com.google.common.collect.ImmutableSet;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@AutoService(Processor.class)
public class EntityNormalizerProcessor extends AutoServiceProcessor {
    private ProcessingEnvironment processingEnv;

    private ITemplateGenerator[] templateGenerators = {
            new EntityTemplateGenerator(),
            new NormalizedStoreTemplateGenerator()
    };

    @Override
    public Set<String> getSupportedOptions() {
        return new HashSet<>();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    @Override
    public ImmutableSet<String> getSupportedAnnotationTypes() {
        return ImmutableSet.of(EntitySpec.class.getCanonicalName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!annotations.isEmpty()) {
            for (ITemplateGenerator templateGenerator : templateGenerators) {
                for (TypeElement typeElement : annotations) {
                    if (templateGenerator.canProcess(typeElement)) {
                        templateGenerator.process(roundEnv.getElementsAnnotatedWith(typeElement), processingEnv);
                    }
                }

                for (Template template : templateGenerator.getTemplates().values()) {
                    try {
                        template.buildJavaFile().writeTo(processingEnv.getFiler());
                    } catch (IOException e) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.toString());
                    }
                }
            }
        }

        return false;
    }
}
