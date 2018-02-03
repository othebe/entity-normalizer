package annotations.processor.templategenerators;

import annotations.EntityId;
import annotations.EntitySpec;
import annotations.processor.ITemplateGenerator;
import annotations.processor.Template;
import com.squareup.javapoet.*;
import core.IEntity;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import java.util.*;

/**
 * Generates a NormalizedEntityStore template from EntitySpec annotated files.
 */
public class NormalizedEntityRepositoryTemplateGenerator implements ITemplateGenerator {
    private final String PACKAGE = "entitynormalizer.store";
    private final String CLASSNAME = "NormalizedEntityRepository";

    private final Map<String, Template> templates;

    private final Set<TypeName> entityClasses;
    private final Map<String, TypeName> typeNameByGeneratedClassName;

    public NormalizedEntityRepositoryTemplateGenerator() {
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
        template.add(ClassName.get(RepositoryReaderInterfaceTemplateGenerator.PACKAGE, RepositoryReaderInterfaceTemplateGenerator.CLASSNAME));
        template.add(ClassName.get(RepositoryWriterInterfaceTemplateGenerator.PACKAGE, RepositoryWriterInterfaceTemplateGenerator.CLASSNAME));

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
     * Generates a putter method for an Entity that returns a Set of Entities that have been modified.
     * @param entitySpecElement EntitySpec annotated element.
     * @param entityFieldSpecs Map of Entity (map) fieldSpecs by Entity types.
     * @param processingEnv Processing environment.
     * @return put(Entity) -> Set<IEntity> methodSpec.
     */
    private MethodSpec getPutterForEntity(Element entitySpecElement, Map<TypeName, FieldSpec> entityFieldSpecs, ProcessingEnvironment processingEnv) {
        TypeName entityType = Utils.getEntityType(entitySpecElement, processingEnv);

        ParameterizedTypeName Set_Entity = ParameterizedTypeName.get(Set.class, IEntity.class);
        ParameterizedTypeName HashSet_Entity = ParameterizedTypeName.get(HashSet.class, IEntity.class);

        ParameterSpec entity = ParameterSpec.builder(entityType, "entity").build();

        MethodSpec.Builder builder = MethodSpec.methodBuilder("put")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(entity)
                .returns(Set_Entity);

        // Store modified entities.
        FieldSpec dirty = FieldSpec.builder(Set_Entity, "dirty").build();
        builder.addStatement("$T $N = new $T()", Set_Entity, dirty, HashSet_Entity);

        // Mark current entity as dirty.
        builder.addStatement("$N.add($N)", dirty, entity);
        // Add current entity to the store.
        builder.addStatement("$N.put($N.id(), $N)", entityFieldSpecs.get(entityType), entity, entity);

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
                        dirty,
                        entityFieldSpecs);
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
                builder.addStatement("$N.put($N.id(), $N)", entityFieldSpecs.get(enclosedEntityType), enclosedEntity, enclosedEntity);
            }
        }

        builder.addStatement("return $N", dirty);

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
                .addModifiers(Modifier.PUBLIC)
                .addParameter(id)
                .returns(entityType);

        FieldSpec cached = FieldSpec.builder(entityType, "cached").build();
        builder.addStatement("$T $N = $N.get($N)", entityType, cached, entityFieldSpecs.get(entityType), id);

        builder.beginControlFlow("if ($N == null)", cached);
        builder.addStatement("return null");
        builder.endControlFlow();

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

            TypeName enclosedElementType = TypeName.get(enclosedElement.asType());

            // Handle nested entities.
            boolean isParameterizable = Utils.isList(enclosedElementType) || Utils.isMap(enclosedElementType);
            if (isParameterizable && !Utils.getParameterizedEntities(enclosedElementType, entityClasses, typeNameByGeneratedClassName).isEmpty()) {
                // Extract parameterized type.
                FieldSpec source = FieldSpec.builder(
                        enclosedElementType,
                        enclosedElement.getSimpleName().toString()
                ).build();
                builder.addStatement("$T $N = $N.get$L()",
                        source.type,
                        source,
                        cached,
                        Utils.convertToPascalCase(enclosedElement.getSimpleName().toString(), processingEnv.getLocale()));

                // Create a copy of the parameterized type.
                FieldSpec sourceCopy = FieldSpec.builder(
                        enclosedElementType,
                        String.format("%sCopy", enclosedElement.getSimpleName().toString())
                ).build();
                builder.addStatement("$T $N = new $T()",
                        sourceCopy.type,
                        sourceCopy,
                        convertAbstractTypeToReal(sourceCopy.type));

                CodeBlock codeBlock = generateGetterCodeBlock(
                        source,
                        sourceCopy,
                        0,
                        entityFieldSpecs);
                builder.addCode(codeBlock);

                constructorString.append("$N");
                constructorArgs.add(sourceCopy);

                if (iterator.hasNext()) {
                    constructorString.append(", ");
                }
            }

            // Entity.
            else if (typeNameByGeneratedClassName.containsKey(enclosedElementType.toString())) {
                TypeName generatedType = typeNameByGeneratedClassName.get(enclosedElementType.toString());

                constructorString.append("$N.get($N.get$L().id())");
                constructorArgs.add(entityFieldSpecs.get(generatedType));
                constructorArgs.add(cached);
                constructorArgs.add(Utils.convertToPascalCase(enclosedElement.getSimpleName().toString(), processingEnv.getLocale()));

                if (iterator.hasNext()) {
                    constructorString.append(", ");
                }
            }

            // Default.
            else {
                constructorString.append(String.format("$N.get%s()", Utils.convertToPascalCase(enclosedElement.toString(), processingEnv.getLocale())));
                constructorArgs.add(cached);

                if (iterator.hasNext()) {
                    constructorString.append(", ");
                }
            }
        }

        // Add entity type name to list so that it is included in array.
        constructorArgs.add(0, entityType);

        Object[] constructorArgsArray = new Object[constructorArgs.size()];
        constructorArgs.toArray(constructorArgsArray);

        builder.addStatement("return new $T(" + constructorString.toString() + ")", constructorArgsArray);

        return builder.build();
    }

    /**
     * Generates a CodeBlock to add Entities in a type to the store and dirty set.
     * @param source The type containing Entities.
     * @param depth Recursion depth.
     * @param dirty Dirty Entity field.
     * @param entityFieldSpecs Map of Entity fields by TypeName.
     * @return Codeblock.
     */
    private CodeBlock generatePutterCodeBlock(FieldSpec source, int depth, FieldSpec dirty, Map<TypeName, FieldSpec> entityFieldSpecs) {
        CodeBlock.Builder builder = CodeBlock.builder();

        TypeName sourceType = source.type;

        boolean isParameterizedList = sourceType instanceof ParameterizedTypeName && Utils.isList(sourceType);
        boolean isParameterizedMap = sourceType instanceof ParameterizedTypeName && Utils.isMap(sourceType);

        if (Utils.getParameterizedEntities(sourceType, entityClasses, typeNameByGeneratedClassName).isEmpty()) {
            builder.addStatement("$N.add($N)", dirty, source);
            builder.addStatement("$N.put($N.id(), $N)", entityFieldSpecs.get(source.type), source, source);
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

            CodeBlock nestedCode = generatePutterCodeBlock(nestedSource, depth + 1, dirty, entityFieldSpecs);
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
                CodeBlock nestedCode = generatePutterCodeBlock(nestedKeySource, depth + 1, dirty, entityFieldSpecs);
                builder.add(nestedCode);
            }

            if (!Utils.getParameterizedEntities(valueParameter, entityClasses, typeNameByGeneratedClassName).isEmpty()) {
                FieldSpec nestedValueSource = FieldSpec.builder(valueParameter, String.format("value%d", depth)).build();
                builder.addStatement("$T $N = $N.get($N)",
                        nestedValueSource.type,
                        nestedValueSource,
                        source,
                        nestedKeySource);
                CodeBlock nestedCode = generatePutterCodeBlock(nestedValueSource, depth + 1, dirty, entityFieldSpecs);
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
     * @param depth Recursion depth.
     * @param entityFieldSpecs Map of Entity fields by TypeName.
     * @return Codeblock.
     */
    private CodeBlock generateGetterCodeBlock(FieldSpec source, FieldSpec copy, int depth, Map<TypeName, FieldSpec> entityFieldSpecs) {
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

                CodeBlock nestedCode = generateGetterCodeBlock(nestedSource, nestedSourceCopy, depth + 1, entityFieldSpecs);
                builder.add(nestedCode);

                builder.addStatement("$N.add($N)",
                        copy,
                        nestedSourceCopy);
            } else {
                builder.addStatement("$N.add($N.get($N.id()))",
                        copy,
                        entityFieldSpecs.get(nestedSource.type),
                        nestedSource);
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

                CodeBlock nestedCode = generateGetterCodeBlock(nestedKeySource, nestedKeySourceCopy, depth + 1, entityFieldSpecs);
                builder.add(nestedCode);
            }

            FieldSpec nestedValueSourceCopy = null;
            if (!Utils.getParameterizedEntities(valueParameter, entityClasses, typeNameByGeneratedClassName).isEmpty()) {
                nestedValueSourceCopy = FieldSpec.builder(valueParameter, String.format("value%dCopy", depth)).build();
                builder.addStatement("$T $N = new $T()",
                        nestedValueSourceCopy.type,
                        nestedValueSourceCopy,
                        convertAbstractTypeToReal(nestedValueSourceCopy.type));

                CodeBlock nestedCode = generateGetterCodeBlock(nestedValueSource, nestedValueSourceCopy, depth + 1, entityFieldSpecs);
                builder.add(nestedCode);
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
