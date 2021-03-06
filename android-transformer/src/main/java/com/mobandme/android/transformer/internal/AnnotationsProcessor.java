/* 
 * Copyright Txus Ballesteros 2015 (@txusballesteros)
 *
 * This file is part of some open source application.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 * Contact: Txus Ballesteros <txus.ballesteros@gmail.com>
 */

package com.mobandme.android.transformer.internal;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.ArrayList;
import java.io.IOException;
import java.util.Collection;
import java.io.BufferedWriter;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.lang.model.util.Types;
import javax.lang.model.SourceVersion;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.AbstractProcessor;

import com.mobandme.android.transformer.Mapped;
import com.mobandme.android.transformer.Mappable;
import javax.annotation.processing.SupportedSourceVersion;
import javax.annotation.processing.SupportedAnnotationTypes;


@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedAnnotationTypes({
    "com.mobandme.android.transformer.Mapping",
    "com.mobandme.android.transformer.Mappable"
})
public class AnnotationsProcessor extends AbstractProcessor {

    RoundEnvironment roundEnvironment;
    Map<String, MapperInfo> mappersList;
    
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        roundEnvironment = roundEnv;
        mappersList = new HashMap<>();

        processMappableAnnotationElements();

        processMappingAnnotationElements();

        buildMapperObjects();

        buildTransformerJavaFile();
        
        return true;
    }

    private void buildTransformerJavaFile() {
        try {

            if (mappersList.size() > 0) {
                MapperInfo firstMapper = (MapperInfo)mappersList.values().toArray()[0];
                
                String packageName = String.format(Tools.TRANSFORMER_PACKAGE_PATTERN, firstMapper.packageName);
                String className = Tools.TRANSFORMER_CLASS_NAME;

                writeTrace(String.format("Generating source file for Transformer class with name %s.%s", packageName, className));

                JavaFileObject javaFileObject = processingEnv.getFiler().createSourceFile(className);
                BufferedWriter buffer = new BufferedWriter(javaFileObject.openWriter());

                buffer.append(String.format(Tools.PACKAGE_PATTERN, packageName));
                buffer.newLine();

                //region "Class Imports Generation"

                buffer.newLine();
                buffer.append(String.format(Tools.IMPORT_PATTERN, "com.mobandme.android.transformer.internal", "AbstractTransformer"));
                for (MapperInfo mapper : mappersList.values()) {

                    buffer.newLine();
                    buffer.append(String.format(Tools.IMPORT_PATTERN, mapper.mapperPackageName, mapper.mapperClassName));
                }

                //endregion

                //region "Class Generation"

                buffer.newLine();
                buffer.newLine();
                buffer.append(String.format(Tools.TRANSFORMER_CLASS_PATTERN, className));
                
                //region "Constructor Generation"

                buffer.newLine();
                buffer.append(String.format("\tpublic %s() {", className));
                buffer.newLine();
                buffer.append("\t\tsuper();");
                
                //region "Variable Inicialization"

                buffer.newLine();
                for (MapperInfo mapper : this.mappersList.values()) {
                    buffer.newLine();
                    buffer.append(String.format("\t\taddMapper(\"%s.%s\", new %s());", mapper.packageName, mapper.className, mapper.mapperClassName));
                    buffer.newLine();
                    buffer.append(String.format("\t\taddMapper(\"%s.%s\", new %s());", mapper.linkedPackageName, mapper.linkedClassName, mapper.mapperClassName));
                }
                
                //endregion
                
                buffer.newLine();
                buffer.append("\t}");
                
                //endregion

                buffer.newLine();
                buffer.append("}");

                //endregion

                buffer.close();
            }
            
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }
    
    private void buildMapperObjects() {
        for (MapperInfo mapper : this.mappersList.values()) {
            Collection<String> mapperImports = new ArrayList<>();
            Collection<String> directFields = new ArrayList<>();
            Collection<String> inverseFields = new ArrayList<>();

            mapperImports.add("import java.util.ArrayList;");
            mapperImports.add("import java.util.Collection;");
            mapperImports.add(String.format(Tools.IMPORT_PATTERN, mapper.packageName, mapper.className));
            mapperImports.add(String.format(Tools.IMPORT_PATTERN, mapper.linkedPackageName, mapper.linkedClassName));

            for (MapperFieldInfo mapperField : mapper.getFields()) {
                String originFieldName = mapperField.fieldName;
                String destinationFieldName = mapperField.fieldName;
                
                if (mapperField.withFieldName != null && !mapperField.withFieldName.trim().equals(""))
                    destinationFieldName = mapperField.withFieldName;
                
                directFields.add(String.format(Tools.MAPPER_FIELD_PATTERN, destinationFieldName, originFieldName));
                inverseFields.add(String.format(Tools.MAPPER_FIELD_PATTERN, originFieldName, destinationFieldName));
            }

            generateMapperJavaFile(mapper, mapperImports, directFields, inverseFields);
        }
    }
    
    private void generateMapperJavaFile(MapperInfo mapper, Collection<String> imports, Collection<String> directFields, Collection<String> inverseFields) {

        try {

            writeTrace(String.format("Generating source file for Mapper with name %s.%s", mapper.mapperPackageName, mapper.mapperClassName));
            
            JavaFileObject javaFileObject = processingEnv.getFiler().createSourceFile(mapper.mapperClassName);
            BufferedWriter buffer = new BufferedWriter(javaFileObject.openWriter());
            
            buffer.append(String.format(Tools.PACKAGE_PATTERN, mapper.mapperPackageName));
            buffer.newLine();
            
            for (String classImport : imports) {
                buffer.newLine();
                buffer.append(classImport);
            }

            buffer.newLine();
            buffer.newLine();
            buffer.append(String.format(Tools.CLASS_PATTERN, mapper.mapperClassName));

            generateTransformMethod(buffer, mapper.className, mapper.linkedClassName, directFields);
            generateTransformMethod(buffer, mapper.linkedClassName, mapper.className, inverseFields);

            buffer.newLine();
            buffer.append("}");
            buffer.close();
            
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }
    
    private void generateTransformMethod(BufferedWriter buffer, String className, String linkedClassName, Collection<String> fields) throws IOException {
        buffer.newLine();
        buffer.newLine();
        buffer.append(String.format("\tpublic %s transform(%s data) {", linkedClassName, className));
        buffer.newLine();
        buffer.append(String.format("\t\t%s result = null;", linkedClassName));

        buffer.newLine();
        buffer.newLine();
        buffer.append("\t\tif (data != null) {");

        buffer.newLine();
        buffer.append(String.format("\t\t\tresult = new %s();", linkedClassName));
        buffer.newLine();
        
        for(String field : fields) {
            buffer.newLine();
            buffer.append(String.format("\t\t\t%s", field));
        }

        buffer.newLine();
        buffer.append("\t\t}");
        buffer.newLine();
        buffer.newLine();
        buffer.append("\t\treturn result;");
        buffer.newLine();
        buffer.append("\t}");
    }
    
    private void processMappableAnnotationElements() {
        for (Element mappableElement : roundEnvironment.getElementsAnnotatedWith(Mappable.class)) {
            if (mappableElement.getKind() == ElementKind.CLASS) {

                AnnotationMirror annotationMirror = getAnnotationMirror(mappableElement, Mappable.class);
                AnnotationValue  annotationValue = getAnnotationValue(annotationMirror, "with");
                TypeElement linkedElement = getTypeElement(annotationValue);
                
                ClassInfo mappableClassInfo = extractClassInformation(mappableElement);
                ClassInfo linkedClassInfo = extractClassInformation(linkedElement);

                if (!haveMapper(mappableClassInfo))
                    createMapper(mappableClassInfo, linkedClassInfo);
            }
        }
    }

    private void processMappingAnnotationElements() {
        for (Element mappingElement : roundEnvironment.getElementsAnnotatedWith(Mapped.class)) {
            if (mappingElement.getKind() == ElementKind.FIELD) {
                Mapped mappingAnnotation = mappingElement.getAnnotation(Mapped.class);

                String fieldName = mappingElement.getSimpleName().toString();
                String toFieldName = mappingAnnotation.toField();
                
                MapperFieldInfo mappingFieldInfo = new MapperFieldInfo(fieldName, toFieldName);
                
                ClassInfo classInfo = extractClassInformationFromField(mappingElement);
                getMapper(classInfo)
                        .getFields()
                            .add(mappingFieldInfo);
            }
        }
    }

    private boolean haveMapper(ClassInfo classInfo) {
        String mapperClassFullName = classInfo.getFullName();
        boolean result = mappersList.containsKey(mapperClassFullName);
        return result;
    }

    private MapperInfo createMapper(ClassInfo classInfo, ClassInfo linkedClassInfo) {
        MapperInfo mapper = new MapperInfo(classInfo.packageName, classInfo.className, linkedClassInfo.packageName, linkedClassInfo.className);
        mappersList.put(mapper.getFullName(), mapper);
        return mapper;
    }

    private MapperInfo getMapper(ClassInfo classInfo) {
        MapperInfo result = mappersList.get(classInfo.getFullName());
        return result;
    }
    
    private ClassInfo extractClassInformationFromField(Element element) {
        Element classElement = element.getEnclosingElement();
        return extractClassInformation(classElement);
    }
    
    private ClassInfo extractClassInformation(Element element) {
        PackageElement packageElement = (PackageElement)element.getEnclosingElement();
        String className = element.getSimpleName().toString();
        String packageName = packageElement.getQualifiedName().toString();
        
        return new ClassInfo(packageName, className);
    }
    
    private AnnotationMirror getAnnotationMirror(Element element, Class<?> annotationType) {
        AnnotationMirror result = null;
        
        String annotationClassName = annotationType.getName();
        for(AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if(mirror.getAnnotationType().toString().equals(annotationClassName)) {
                result = mirror;
                break;
            }
        }
        
        return result;
    }
    
    private AnnotationValue getAnnotationValue(AnnotationMirror annotation, String field) {
        AnnotationValue result = null;

        if (annotation != null) {
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotation.getElementValues().entrySet()) {
                if (entry.getKey().getSimpleName().toString().equals(field)) {
                    return entry.getValue();
                }
            }
        }
        
        return result;
    }

    private TypeElement getTypeElement(AnnotationValue value) {
        TypeElement result = null;
        
        if (value != null) {
            TypeMirror typeMirror = (TypeMirror)value.getValue();
            Types TypeUtils = processingEnv.getTypeUtils();
            result = (TypeElement)TypeUtils.asElement(typeMirror);
        }
        
        return result;
    }
    
    private void writeTrace(String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message);
    }
    
    private class ClassInfo {
        public final String className;
        public final String packageName;

        public ClassInfo(String packageName, String className) {
            this.packageName = packageName;
            this.className = className;
        }

        public String getFullName() {
            return String.format("%s.%s", packageName, className);
        }
        
        @Override
        public String toString() {
            return String.format("%s.%s", packageName, className);
        }
    }
    
    private class MapperInfo extends ClassInfo {
        public final String mapperClassName;
        public final String mapperPackageName;
        public final String linkedClassName;
        public final String linkedPackageName;

        private List<MapperFieldInfo> mappingsList = new ArrayList<>();
        
        public List<MapperFieldInfo> getFields() { return mappingsList; }
        
        public MapperInfo(String packageName, String className, String linkedPackageName, String linkedClassName) {
            super(packageName, className);
            
            this.mapperClassName = String.format(Tools.MAPPER_CLASS_NAME_PATTERN, className);
            this.mapperPackageName = String.format(Tools.MAPPER_PACKAGE_PATTERN, packageName);
            this.linkedPackageName = linkedPackageName;
            this.linkedClassName = linkedClassName;
        }
    }
    
    private class MapperFieldInfo {
        public final String fieldName;
        public final String withFieldName;
        
        public MapperFieldInfo(String fieldName, String withFieldName) {
            this.fieldName = fieldName;
            this.withFieldName = withFieldName;
        }
    }
}
