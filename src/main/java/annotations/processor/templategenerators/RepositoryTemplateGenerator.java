package annotations.processor.templategenerators;

import annotations.EntitySpec;
import annotations.processor.ITemplateGenerator;
import annotations.processor.Template;
import com.squareup.javapoet.*;
import core.IEntity;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import java.util.*;

/**
 * Generates a in-memory store template from EntitySpec annotated files.
 */
public class RepositoryTemplateGenerator implements ITemplateGenerator {
    private final String PACKAGE = "entitynormalizer.store";
    private final String CLASSNAME = "NormalizedEntityRepository";

    private final Map<String, Template> templates;

    private final Set<TypeName> entityClasses;
    private final Map<String, TypeName> typeNameByGeneratedClassName;

    public RepositoryTemplateGenerator() {
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
        Template template = new Template(PACKAGE, CLASSNAME);

        for (Element entitySpec : entitySpecs) {
            ClassName entityType = Utils.getEntityType(entitySpec, processingEnv);

            typeNameByGeneratedClassName.put(entityType.simpleName(), entityType);
            entityClasses.add(entityType);
        }

        // Builder.
        template.add(getTypeSpec_Builder());
        template.add(getFieldSpec_builder());

        // IEntityStoreReader chain.
        FieldSpec readers = getFieldSpec_readers();
        template.add(readers);

        // IEntityStoreWriter chain.
        FieldSpec writers = getFieldSpec_writers();
        template.add(writers);

        // Constructor.
        template.add(getConstructor(readers, writers));

        // Generate getters and setters for every Entity.
        for (Element entitySpec : entitySpecs) {
            template.add(getPutterForEntity(entitySpec, writers, processingEnv));
            template.add(getGetterForEntity(entitySpec, readers, processingEnv));
        }

        // Add reader and writer interfaces.
        template.add(ClassName.get(RepositoryReaderInterfaceTemplateGenerator.PACKAGE, RepositoryReaderInterfaceTemplateGenerator.CLASSNAME));
        template.add(ClassName.get(RepositoryWriterInterfaceTemplateGenerator.PACKAGE, RepositoryWriterInterfaceTemplateGenerator.CLASSNAME));

        templates.put(CLASSNAME, template);
    }

    @Override
    public Map<String, Template> getTemplates() {
        return templates;
    }


    /**
     * Generates an instance for the Builder class.
     * @return Builder methodSpec.
     */
    private MethodSpec getFieldSpec_builder() {
        ClassName builder = ClassName.bestGuess("Builder");
        return MethodSpec.methodBuilder("builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(builder)
                .addStatement("return new $T()", builder)
                .build();
    }

    /**
     * Generates the readers array field.
     * @return readers fieldSpec.
     */
    private FieldSpec getFieldSpec_readers() {
        ClassName readerType = ClassName.get(StoreReaderInterfaceTemplateGenerator.PACKAGE, StoreReaderInterfaceTemplateGenerator.CLASSNAME);
        TypeName readerTypeArray = ArrayTypeName.of(readerType);

        return FieldSpec.builder(readerTypeArray, "readers", Modifier.PRIVATE)
                .build();
    }

    /**
     * Generates the writers array field.
     * @return writers fieldSpec.
     */
    private FieldSpec getFieldSpec_writers() {
        ClassName writerType = ClassName.get(StoreWriterInterfaceTemplateGenerator.PACKAGE, StoreWriterInterfaceTemplateGenerator.CLASSNAME);
        TypeName writerTypeArray = ArrayTypeName.of(writerType);

        return FieldSpec.builder(writerTypeArray, "writers", Modifier.PRIVATE)
                .build();
    }

    /**
     * Generates a constructor to take in reader and writer chains.
     * @param readers Varargs reader chain.
     * @param writers Varargs writer chain.
     * @return Constructor methodSpec.
     */
    private MethodSpec getConstructor(FieldSpec readers, FieldSpec writers) {
        ParameterSpec readerParameterSpec = ParameterSpec.builder(readers.type, "readers").build();
        ParameterSpec writerParameterSpec = ParameterSpec.builder(writers.type, "writers").build();

        return MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(readerParameterSpec)
                .addParameter(writerParameterSpec)
                .addStatement("this.$N = $N", readers, readerParameterSpec)
                .addStatement("this.$N = $N", writers, writerParameterSpec)
                .build();
    }

    /**
     * Generates a putter method for an Entity that returns a Set of Entities that have been modified.
     * @param entitySpecElement EntitySpec annotated element.
     * @param writers Array of store writers.
     * @param processingEnv Processing environment.
     * @return put(Entity) -> Set<IEntity> methodSpec.
     */
    private MethodSpec getPutterForEntity(Element entitySpecElement, FieldSpec writers, ProcessingEnvironment processingEnv) {
        TypeName entityType = Utils.getEntityType(entitySpecElement, processingEnv);

        ParameterizedTypeName Set_Entity = ParameterizedTypeName.get(Set.class, IEntity.class);
        ParameterizedTypeName HashSet_Entity = ParameterizedTypeName.get(HashSet.class, IEntity.class);

        ParameterSpec entity = ParameterSpec.builder(entityType, "entity").build();

        MethodSpec.Builder builder = MethodSpec.methodBuilder("put")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(entity)
                .returns(Set_Entity);

        // Store modified entities.
        FieldSpec dirty = FieldSpec.builder(Set_Entity, "dirty").build();
        builder.addStatement("$T $N = new $T()", Set_Entity, dirty, HashSet_Entity);

        // Mark current entity as dirty.
        builder.addStatement("$N.add($N)", dirty, entity);
        // Add current entity to the stores.
        ClassName writerType = ClassName.get(StoreWriterInterfaceTemplateGenerator.PACKAGE, StoreWriterInterfaceTemplateGenerator.CLASSNAME);
        FieldSpec writer = FieldSpec.builder(writerType, "reader").build();
        builder.beginControlFlow("for ($T $N : $N)", writer.type, writer, writers);
        builder.addStatement("$N.put($N)", writer, entity);
        builder.endControlFlow();

        // Store all Entities that appear as properties within this Entity.
        for (Element enclosedElement : entitySpecElement.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.FIELD) {
                continue;
            }

            // Handle nested Entities.
            TypeName enclosedElementType = TypeName.get(enclosedElement.asType());
            boolean isParameterizable = Utils.isList(enclosedElementType) || Utils.isMap(enclosedElementType);
            if (isParameterizable && !Utils.getParameterizedEntities(enclosedElementType, entityClasses, typeNameByGeneratedClassName).isEmpty()) {
                // Extract parameterized Entity in field.
                FieldSpec source = FieldSpec.builder(
                        enclosedElementType,
                        enclosedElement.getSimpleName().toString()
                ).build();
                builder.addStatement("$T $N = $N.get$L()",
                        source.type,
                        source,
                        entity,
                        Utils.convertToPascalCase(enclosedElement.getSimpleName().toString(), processingEnv.getLocale()));

                // Add put CodeBlock.
                CodeBlock putter = generatePutterCodeBlock(
                        source,
                        0,
                        dirty);
                builder.addCode(putter);
            }

            // Handle un-nested Entity.
            else if (typeNameByGeneratedClassName.containsKey(enclosedElementType.toString())) {
                TypeName enclosedEntityType = typeNameByGeneratedClassName.get(enclosedElementType.toString());
                FieldSpec enclosedEntity = FieldSpec.builder(
                        enclosedElementType,
                        enclosedElement.getSimpleName().toString()
                ).build();
                builder.addStatement("$T $N = $N.get$L()",
                        enclosedEntity.type,
                        enclosedEntity,
                        entity,
                        Utils.convertToPascalCase(enclosedEntity.name, processingEnv.getLocale()));

                builder.addStatement("$N.add($N)", dirty, enclosedEntity);
                builder.addStatement("put($N)", enclosedEntity);
            }
        }

        builder.addStatement("return $N", dirty);

        return builder.build();
    }

    /**
     * Generates a getter method for an Entity.
     * @param entitySpecElement EntitySpec annotated element.
     * @param readers Array of store readers.
     * @param processingEnv Processing environment.
     * @return getEntity(ID) -> Entity methodSpec.
     */
    private MethodSpec getGetterForEntity(Element entitySpecElement, FieldSpec readers, ProcessingEnvironment processingEnv) {
        ClassName entityType = Utils.getEntityType(entitySpecElement, processingEnv);

        ParameterSpec id = ParameterSpec.builder(Utils.getIdTypeName(entitySpecElement), "id").build();

        MethodSpec.Builder builder = MethodSpec.methodBuilder(String.format("get%s", entityType.simpleName()))
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(id)
                .returns(entityType);

        FieldSpec cached = FieldSpec.builder(entityType, "cached").build();
        builder.addStatement("$T $N = null", cached.type, cached);

        // Loop through readers.
        ClassName readerType = ClassName.get(StoreReaderInterfaceTemplateGenerator.PACKAGE, StoreReaderInterfaceTemplateGenerator.CLASSNAME);
        FieldSpec reader = FieldSpec.builder(readerType, "reader").build();
        builder.beginControlFlow("for ($T $N : $N)", reader.type, reader, readers);
        builder.addStatement("$N = $N.get$L($N)", cached, reader, entityType.simpleName(), id);
        builder.beginControlFlow("if ($N != null)", cached);
        builder.addStatement("break");
        builder.endControlFlow();
        builder.endControlFlow();

        builder.beginControlFlow("if ($N == null)", cached);
        builder.addStatement("return null");
        builder.endControlFlow();

        FieldSpec dirty = FieldSpec.builder(TypeName.BOOLEAN, "dirty").build();
        builder.addStatement("$T $N = false", dirty.type, dirty);

        // Constructor string.
        StringBuilder constructorString = new StringBuilder();
        // Constructor arguments for code generation.
        List<Object> constructorArgs = new LinkedList<>();

        // Construct a new Entity from its properties.
        Iterator<? extends Element> iterator = entitySpecElement.getEnclosedElements().iterator();
        while (iterator.hasNext()) {
            Element enclosedElement = iterator.next();

            if (enclosedElement.getKind() != ElementKind.FIELD) {
                continue;
            }

            String enclosedElementName = Utils.convertToPascalCase(enclosedElement.getSimpleName().toString(), processingEnv.getLocale());

            TypeName enclosedElementType = TypeName.get(enclosedElement.asType());

            FieldSpec enclosedElementField = FieldSpec.builder(enclosedElementType, enclosedElement.getSimpleName().toString()).build();

            // Handle nested entities.
            boolean isParameterizable = Utils.isList(enclosedElementType) || Utils.isMap(enclosedElementType);
            if (isParameterizable && !Utils.getParameterizedEntities(enclosedElementType, entityClasses, typeNameByGeneratedClassName).isEmpty()) {
                // Dirty parameterized type flag.
                FieldSpec nestedDirty = FieldSpec.builder(TypeName.BOOLEAN, String.format("%sDirty", enclosedElement.getSimpleName().toString())).build();
                builder.addStatement("$T $N = false", nestedDirty.type, nestedDirty);

                // Extract parameterized type.
                FieldSpec source = enclosedElementField;
                builder.addStatement("$T $N = $N.get$L()",
                        source.type,
                        source,
                        cached,
                        enclosedElementName);

                // Create a copy of the parameterized type.
                FieldSpec sourceCopy = FieldSpec.builder(
                        enclosedElementType,
                        String.format("%sCopy", source.name)
                ).build();
                builder.addStatement("$T $N = new $T()",
                        sourceCopy.type,
                        sourceCopy,
                        convertAbstractTypeToReal(sourceCopy.type));

                CodeBlock codeBlock = generateGetterCodeBlock(
                        source,
                        sourceCopy,
                        nestedDirty,
                        0);
                builder.addCode(codeBlock);

                // Check for equality and add to constructor.
                builder.beginControlFlow("if ($N)", nestedDirty);
                builder.addStatement("$N = true", dirty);
                builder.nextControlFlow("else");
                builder.addStatement("$N = $N", sourceCopy, source);
                builder.endControlFlow();

                constructorString.append("$N");
                constructorArgs.add(sourceCopy);
            }

            // Entity.
            else if (typeNameByGeneratedClassName.containsKey(enclosedElementType.toString())) {
                builder.addStatement("$T $N = get$L($N.get$L().id())",
                        enclosedElementField.type,
                        enclosedElementField,
                        enclosedElementType.toString(),
                        cached,
                        enclosedElementName);

                // Check for equality and add to constructor.
                builder.beginControlFlow("if (!($N.get$L().equals($N)))",
                        cached,
                        enclosedElementName,
                        enclosedElementField);
                builder.addStatement("$N = true", dirty);
                builder.endControlFlow();

                constructorString.append("$N");
                constructorArgs.add(enclosedElementField);
            }

            // Default.
            else {
                builder.addStatement("$T $N = $N.get$L()", enclosedElementField.type, enclosedElementField, cached, enclosedElementName);

                constructorString.append("$N");
                constructorArgs.add(enclosedElementField);
            }

            if (iterator.hasNext()) {
                constructorString.append(", ");
            }
        }

        // Add entity type name to list so that it is included in array.
        constructorArgs.add(0, entityType);

        Object[] constructorArgsArray = new Object[constructorArgs.size()];
        constructorArgs.toArray(constructorArgsArray);

        builder.beginControlFlow("if ($N)", dirty);
        builder.addStatement("return new $T(" + constructorString.toString() + ")", constructorArgsArray);
        builder.nextControlFlow("else");
        builder.addStatement("return $N", cached);
        builder.endControlFlow();

        return builder.build();
    }

    /**
     * Generates a CodeBlock to add Entities in a type to the store and dirty set.
     * @param source The type containing Entities.
     * @param depth Recursion depth.
     * @param dirty Dirty Entity field.
     * @return Codeblock.
     */
    private CodeBlock generatePutterCodeBlock(FieldSpec source, int depth, FieldSpec dirty) {
        CodeBlock.Builder builder = CodeBlock.builder();

        TypeName sourceType = source.type;

        boolean isParameterizedList = sourceType instanceof ParameterizedTypeName && Utils.isList(sourceType);
        boolean isParameterizedMap = sourceType instanceof ParameterizedTypeName && Utils.isMap(sourceType);

        if (Utils.getParameterizedEntities(sourceType, entityClasses, typeNameByGeneratedClassName).isEmpty()) {
            builder.addStatement("$N.add($N)", dirty, source);
            builder.addStatement("put($N)", source);
            return builder.build();
        }

        List<TypeName> parameterTypes = Utils.getParameterTypeNames(sourceType, typeNameByGeneratedClassName);

        if (isParameterizedList) {
            TypeName listParameter = parameterTypes.get(0);
            FieldSpec nestedSource = FieldSpec.builder(listParameter, String.format("item%d", depth)).build();

            builder.beginControlFlow("for ($T $N : $N)",
                    nestedSource.type,
                    nestedSource,
                    source);

            CodeBlock nestedCode = generatePutterCodeBlock(nestedSource, depth + 1, dirty);
            builder.add(nestedCode);

            builder.endControlFlow();
        }

        else if (isParameterizedMap) {
            TypeName keyParameter = parameterTypes.get(0);
            TypeName valueParameter = parameterTypes.get(1);

            FieldSpec nestedKeySource = FieldSpec.builder(keyParameter, String.format("key%d", depth)).build();
            builder.beginControlFlow("for ($T $N : $N.keySet())",
                    nestedKeySource.type,
                    nestedKeySource,
                    source);

            if (!Utils.getParameterizedEntities(keyParameter, entityClasses, typeNameByGeneratedClassName).isEmpty()) {
                CodeBlock nestedCode = generatePutterCodeBlock(nestedKeySource, depth + 1, dirty);
                builder.add(nestedCode);
            }

            if (!Utils.getParameterizedEntities(valueParameter, entityClasses, typeNameByGeneratedClassName).isEmpty()) {
                FieldSpec nestedValueSource = FieldSpec.builder(valueParameter, String.format("value%d", depth)).build();
                builder.addStatement("$T $N = $N.get($N)",
                        nestedValueSource.type,
                        nestedValueSource,
                        source,
                        nestedKeySource);
                CodeBlock nestedCode = generatePutterCodeBlock(nestedValueSource, depth + 1, dirty);
                builder.add(nestedCode);
            }

            builder.endControlFlow();
        }

        return builder.build();
    }

    /**
     * Generates a CodeBlock to get Entities from a source and add the latest versions to the copy.
     * @param source The type containing Entities.
     * @param copy The type that will contain updated versions of the Entities.
     * @param dirty Dirty field.
     * @param depth Recursion depth.
     * @return Codeblock.
     */
    private CodeBlock generateGetterCodeBlock(FieldSpec source, FieldSpec copy, FieldSpec dirty, int depth) {
        CodeBlock.Builder builder = CodeBlock.builder();

        TypeName sourceType = source.type;

        boolean isParameterizedList = sourceType instanceof ParameterizedTypeName && Utils.isList(sourceType);
        boolean isParameterizedMap = sourceType instanceof ParameterizedTypeName && Utils.isMap(sourceType);

        if (Utils.getParameterizedEntities(sourceType, entityClasses, typeNameByGeneratedClassName).isEmpty()) {
            return builder.build();
        }

        List<TypeName> parameterTypes = Utils.getParameterTypeNames(sourceType, typeNameByGeneratedClassName);

        if (isParameterizedList) {
            TypeName listParameter = parameterTypes.get(0);
            FieldSpec nestedSource = FieldSpec.builder(listParameter, String.format("item%d", depth)).build();

            builder.beginControlFlow("for ($T $N : $N)",
                    nestedSource.type,
                    nestedSource,
                    source);

            if (nestedSource.type instanceof ParameterizedTypeName) {
                FieldSpec nestedSourceCopy = FieldSpec.builder(listParameter, String.format("item%dCopy", depth)).build();
                builder.addStatement("$T $N = new $T()",
                        nestedSourceCopy.type,
                        nestedSourceCopy,
                        convertAbstractTypeToReal(nestedSourceCopy.type));

                CodeBlock nestedCode = generateGetterCodeBlock(nestedSource, nestedSourceCopy, dirty, depth + 1);
                builder.add(nestedCode);

                builder.addStatement("$N.add($N)",
                        copy,
                        nestedSourceCopy);
            } else {
                FieldSpec item = FieldSpec.builder(nestedSource.type, "item").build();
                builder.addStatement("$T $N = get$L($N.id())", item.type, item, ((ClassName) nestedSource.type).simpleName(), nestedSource);

                // Check for equality and add to constructor.
                String equalityChecker = item.type.isPrimitive() ?
                        "if ($N == $N)" :
                        "if ($N.equals($N))";
                builder.beginControlFlow(equalityChecker,
                        nestedSource, item);
                builder.addStatement("$N.add($N)", copy, nestedSource);
                builder.nextControlFlow("else");
                builder.addStatement("$N = true", dirty);
                builder.addStatement("$N.add($N)", copy, item);
                builder.endControlFlow();
            }

            builder.endControlFlow();
        }

        else if (isParameterizedMap) {
            TypeName keyParameter = parameterTypes.get(0);
            TypeName valueParameter = parameterTypes.get(1);

            FieldSpec nestedKeySource = FieldSpec.builder(keyParameter, String.format("key%d", depth)).build();
            builder.beginControlFlow("for ($T $N : $N.keySet())",
                    nestedKeySource.type,
                    nestedKeySource,
                    source);

            FieldSpec nestedValueSource = FieldSpec.builder(valueParameter, String.format("value%d", depth)).build();
            builder.addStatement("$T $N = $N.get($N)",
                    nestedValueSource.type,
                    nestedValueSource,
                    source,
                    nestedKeySource);

            FieldSpec nestedKeySourceCopy = null;
            if (!Utils.getParameterizedEntities(keyParameter, entityClasses, typeNameByGeneratedClassName).isEmpty()) {
                nestedKeySourceCopy = FieldSpec.builder(keyParameter, String.format("key%dCopy", depth)).build();
                builder.addStatement("$T $N = new $T()",
                        nestedKeySourceCopy.type,
                        nestedKeySourceCopy,
                        convertAbstractTypeToReal(nestedKeySourceCopy.type));

                CodeBlock nestedCode = generateGetterCodeBlock(nestedKeySource, nestedKeySourceCopy, dirty, depth + 1);
                builder.add(nestedCode);
            } else if (entityClasses.contains(keyParameter)) {
                nestedKeySourceCopy = FieldSpec.builder(keyParameter, String.format("key%dCopy", depth)).build();
                builder.addStatement("$T $N = get$L($N.id())",
                        nestedKeySourceCopy.type,
                        nestedKeySourceCopy,
                        ((ClassName) keyParameter).simpleName(),
                        nestedKeySource);
                // Equality check.
                builder.beginControlFlow("if (!($N.equals($N)))",
                        nestedKeySource,
                        nestedKeySourceCopy);
                builder.addStatement("$N = true", dirty);
                builder.endControlFlow();
            }

            FieldSpec nestedValueSourceCopy = null;
            if (!Utils.getParameterizedEntities(valueParameter, entityClasses, typeNameByGeneratedClassName).isEmpty()) {
                nestedValueSourceCopy = FieldSpec.builder(valueParameter, String.format("value%dCopy", depth)).build();
                builder.addStatement("$T $N = new $T()",
                        nestedValueSourceCopy.type,
                        nestedValueSourceCopy,
                        convertAbstractTypeToReal(nestedValueSourceCopy.type));

                CodeBlock nestedCode = generateGetterCodeBlock(nestedValueSource, nestedValueSourceCopy, dirty, depth + 1);
                builder.add(nestedCode);
            } else if (entityClasses.contains(valueParameter)) {
                nestedValueSourceCopy = FieldSpec.builder(valueParameter, String.format("value%dCopy", depth)).build();
                builder.addStatement("$T $N = get$L($N.id())",
                        nestedValueSourceCopy.type,
                        nestedValueSourceCopy,
                        ((ClassName) valueParameter).simpleName(),
                        nestedValueSource);
                // Equality check.
                builder.beginControlFlow("if (!($N.equals($N)))",
                        nestedValueSource,
                        nestedValueSourceCopy);
                builder.addStatement("$N = true", dirty);
                builder.endControlFlow();
            }

            builder.addStatement("$N.put($N, $N)",
                    copy,
                    nestedKeySourceCopy == null ? nestedKeySource : nestedKeySourceCopy,
                    nestedValueSourceCopy == null ? nestedValueSource : nestedValueSourceCopy);

            builder.endControlFlow();
        }

        return builder.build();
    }

    /**
     * Generates the Builder class.
     * @return
     */
    private TypeSpec getTypeSpec_Builder() {
        FieldSpec readers = getFieldSpec_readers();
        FieldSpec writers = getFieldSpec_writers();

        ParameterSpec readersParameterSpec = ParameterSpec.builder(readers.type, "readers").build();
        ParameterSpec writersParameterSpec = ParameterSpec.builder(writers.type, "writers").build();

        MethodSpec setReaders = MethodSpec.methodBuilder("setReaders")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(readersParameterSpec)
                .varargs(true)
                .returns(ClassName.bestGuess("Builder"))
                .addStatement("this.$N = $N", readers, readersParameterSpec)
                .addStatement("return this")
                .build();

        MethodSpec setWriters = MethodSpec.methodBuilder("setWriters")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(writersParameterSpec)
                .varargs(true)
                .returns(ClassName.bestGuess("Builder"))
                .addStatement("this.$N = $N", writers, writersParameterSpec)
                .addStatement("return this")
                .build();

        // Default in-memory store.
        FieldSpec defaultStore = FieldSpec.builder(ClassName.get(InMemoryStoreTemplateGenerator.PACKAGE, InMemoryStoreTemplateGenerator.CLASSNAME), "defaultStore")
                .build();

        MethodSpec build = MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get(PACKAGE, CLASSNAME))
                // In-memory store instance.
                .addStatement("$T $N = new $T()", defaultStore.type, defaultStore, defaultStore.type)
                // Default getter.
                .beginControlFlow("if ($N == null || $N.length == 0)", readers, readers)
                .addStatement("$N = new $T { $N }",
                        readers,
                        readers.type,
                        defaultStore)
                .endControlFlow()
                // Default setter.
                .beginControlFlow("if ($N == null || $N.length == 0)", writers, writers)
                .addStatement("$N = new $T { $N }",
                        writers,
                        writers.type,
                        defaultStore)
                .endControlFlow()
                // Construct.
                .addStatement("return new $T($N, $N)",
                        ClassName.get(PACKAGE, CLASSNAME),
                        readers,
                        writers)
                .build();

        return TypeSpec.classBuilder("Builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addField(readers)
                .addField(writers)
                .addMethod(setReaders)
                .addMethod(setWriters)
                .addMethod(build)
                .build();

    }

    /**
     * Converts an abstract type to a implemented type. Currently only supports List and Map.
     * @param typeName Possibly abstract type.
     * @return Implemented type, either LinkedList or HashMap.
     */
    private TypeName convertAbstractTypeToReal(TypeName typeName) {
        TypeName[] nestedParameterTypesArray = null;
        TypeName rawType = typeName;

        if (typeName instanceof ParameterizedTypeName) {
            List<TypeName> nestedParameterTypes = Utils.getParameterTypeNames(typeName, typeNameByGeneratedClassName);
            nestedParameterTypesArray = new TypeName[nestedParameterTypes.size()];
            nestedParameterTypes.toArray(nestedParameterTypesArray);

            rawType = ((ParameterizedTypeName) typeName).rawType;
        }

        if (ClassName.get(Map.class).equals(rawType)) {
            ClassName real = ClassName.get(HashMap.class);
            return nestedParameterTypesArray == null ?
                    real :
                    ParameterizedTypeName.get(real, nestedParameterTypesArray);
        } else if (ClassName.get(List.class).equals(rawType)) {
            ClassName real = ClassName.get(LinkedList.class);
            return nestedParameterTypesArray == null ?
                    real :
                    ParameterizedTypeName.get(real, nestedParameterTypesArray);
        } else {
            return typeName;
        }
    }
}
