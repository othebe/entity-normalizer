package annotations.processor;

import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import java.util.LinkedList;
import java.util.List;

public class Template {

    private final String packageName;
    private final String objectName;

    private final List<TypeName> superInterfaces;
    private final List<FieldSpec> fields;
    private final List<MethodSpec> methods;
    private final List<TypeSpec> innerClasses;

    private final boolean isInterface;

    public Template(final String packageName, final String objectName) {
        this(packageName, objectName, false);
    }

    public Template(final String packageName, final String objectName, final boolean isInterface) {
        this.isInterface = isInterface;

        this.packageName = packageName;
        this.objectName = objectName;

        this.superInterfaces = new LinkedList<>();
        this.fields = new LinkedList<>();
        this.methods = new LinkedList<>();
        this.innerClasses = new LinkedList<>();
    }

    public void add(TypeName superInterface) {
        superInterfaces.add(superInterface);
    }

    public void add(FieldSpec fieldSpec) {
        fields.add(fieldSpec);
    }

    public void add(MethodSpec methodSpec) {
        methods.add(methodSpec);
    }

    public void add(TypeSpec innerClass) {
        innerClasses.add(innerClass);
    }

    public JavaFile buildJavaFile() {
        TypeSpec.Builder builder = isInterface ?
                TypeSpec.interfaceBuilder(objectName).addModifiers(Modifier.PUBLIC) :
                TypeSpec.classBuilder(objectName).addModifiers(Modifier.PUBLIC);

        for (TypeName superInterface : superInterfaces) {
            builder.addSuperinterface(superInterface);
        }

        for (FieldSpec fieldSpec : fields) {
            builder.addField(fieldSpec);
        }

        for (MethodSpec methodSpec : methods) {
            builder.addMethod(methodSpec);
        }

        for (TypeSpec innerClass : innerClasses) {
            builder.addType(innerClass);
        }

        return JavaFile.builder(packageName, builder.build()).build();
    }
}
