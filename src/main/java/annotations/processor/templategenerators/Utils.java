package annotations.processor.templategenerators;

import annotations.EntitySpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import java.util.*;

public class Utils {
    public static final TypeName getSafelyBoxedTypeName(TypeName raw) {
        if (raw.isPrimitive()) {
            return raw.box();
        } else {
            return raw;
        }
    }

    public static final String convertToPascalCase(String string, Locale locale) {
        return string.substring(0, 1).toUpperCase(locale) + string.substring(1);
    }

    public static final String convertToCamelCase(String string, Locale locale) {
        return string.substring(0, 1).toLowerCase(locale) + string.substring(1);
    }

    /**
     * Determines if a type is a list.
     * @param element Element type.
     * @return True if list, else false.
     */
    public static final boolean isList(TypeName element) {
        final TypeName LIST_TYPE = ClassName.get(List.class);
        if (element instanceof ParameterizedTypeName) {
            return ((ParameterizedTypeName) element).rawType.equals(LIST_TYPE);
        } else {
            return element.equals(LIST_TYPE);
        }
    }

    /**
     * Determines if a type is a map.
     * @param element Element type.
     * @return True if list, else false.
     */
    public static final boolean isMap(TypeName element) {
        final TypeName MAP_TYPE = ClassName.get(Map.class);
        if (element instanceof ParameterizedTypeName) {
            return ((ParameterizedTypeName) element).rawType.equals(MAP_TYPE);
        } else {
            return element.equals(MAP_TYPE);
        }
    }

    /**
     * Parses EntitySpec annotated element to extract the generated class name.
     * @param entitySpecElement EntitySpec annotated element.
     * @param locale Locale
     * @return The generated class name.
     */
    public static final String getEntityClassNameFromSpec(Element entitySpecElement, Locale locale) {
        EntitySpec entitySpec = entitySpecElement.getAnnotation(EntitySpec.class);

        if (entitySpec == null) {
            throw new RuntimeException("Error: Trying to get generated class name for non EntitySpec");
        }

        String entityName = entitySpec.name();
        String className = entitySpec.className();

        String generatedClassName = className == null || className.isEmpty() ?
                entityName :
                className;

        return  generatedClassName.substring(0, 1).toUpperCase(locale) +
                generatedClassName.substring(1);
    }

    /**
     * Generates a fully qualified type for the Entity defined in the EntitySpec annotated element.
     * @param entitySpec EntitySpec annotated element.
     * @param processingEnv Processing environment.
     * @return Fully qualified Entity type.
     */
    public static final ClassName getEntityType(Element entitySpec, ProcessingEnvironment processingEnv) {
        PackageElement sourcePackageElement = processingEnv.getElementUtils().getPackageOf(entitySpec);
        String generatedClassName = Utils.getEntityClassNameFromSpec(entitySpec, processingEnv.getLocale());

        return ClassName.get(sourcePackageElement.toString(), generatedClassName);
    }

    /**
     * Maps an Entity parameter to a fully qualified type.
     * @param parameterizedTypeName The parameterized type.
     * @param typeNameByGeneratedClassName Map of Entity names to their fully qualified types.
     * @return A parameterized type where Entities are replaced by their fully qualified types.
     */
    public static final ParameterizedTypeName getParameterizedTypeWithGeneratedTypes(ParameterizedTypeName parameterizedTypeName, Map<String, TypeName> typeNameByGeneratedClassName) {
        ClassName rawType = parameterizedTypeName.rawType;

        List<TypeName> typeArguments = parameterizedTypeName.typeArguments;
        TypeName[] typeArgumentsArray = new TypeName[typeArguments.size()];

        int i = 0;
        for (TypeName parameterTypeName : typeArguments) {
            if (parameterTypeName instanceof ParameterizedTypeName) {
                typeArgumentsArray[i] = getParameterizedTypeWithGeneratedTypes((ParameterizedTypeName) parameterTypeName, typeNameByGeneratedClassName);
            } else {
                ClassName className = (ClassName) parameterTypeName;
                String packageName = className.packageName();
                boolean isMissingPackage = packageName == null || packageName.isEmpty();
                boolean isMatchingGeneratedClass = typeNameByGeneratedClassName.containsKey(className.toString());

                if (isMissingPackage && isMatchingGeneratedClass) {
                    typeArgumentsArray[i] = typeNameByGeneratedClassName.get(className.toString());
                } else {
                    typeArgumentsArray[i] = parameterTypeName;
                }
            }

            i++;
        }

        return ParameterizedTypeName.get(rawType, typeArgumentsArray);
    }

    /**
     *
     * @param elementTypeName
     * @return
     */
    public static final List<TypeName> getParameterizedEntities(TypeName elementTypeName, Set<TypeName> entityClasses, Map<String, TypeName> typeNameByGeneratedClassName) {
        List<TypeName> entities = new LinkedList<>();

        if (!(elementTypeName instanceof ParameterizedTypeName)) {
            return entities;
        }

        ParameterizedTypeName cleaned = getParameterizedTypeWithGeneratedTypes(
                (ParameterizedTypeName) elementTypeName,
                typeNameByGeneratedClassName);

        for (TypeName parameterTypeName : cleaned.typeArguments) {
            if (entityClasses.contains(parameterTypeName)) {
                entities.add(parameterTypeName);
            } else {
                entities.addAll(getParameterizedEntities(parameterTypeName, entityClasses, typeNameByGeneratedClassName));
            }
        }

        return entities;
    }

    public static final List<TypeName> getParameterTypeNames(TypeName typeName, Map<String, TypeName> typeNameByGeneratedClassName) {
        List<TypeName> typeNames = new LinkedList<>();
        if (typeName instanceof ParameterizedTypeName) {
            ParameterizedTypeName parameterizedTypeName = getParameterizedTypeWithGeneratedTypes(
                    (ParameterizedTypeName) typeName,
                    typeNameByGeneratedClassName);
            typeNames.addAll(parameterizedTypeName.typeArguments);
        }

        return typeNames;
    }
}
