package io.github.wy.annotation.processor;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * @author rycat
 * @since 2023/11/25
 */
@SupportedAnnotationTypes({"io.github.wy.annotation.Table"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class TableProcessor extends AbstractProcessor {
    private Messager messager;
    private Filer filer;
    
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
        log("init");
    }
    
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        stream(annotations).findAny().ifPresent(tableAnno->{
            stream(tableAnno.getEnclosedElements()).forEach(ele->{
                log(tableAnno.toString(), ele.getKind().toString(), ele.toString());
            });
            
            stream(roundEnv.getElementsAnnotatedWith(tableAnno))
                    .filter(element->ElementKind.CLASS == element.getKind())
                    .map(TypeElement.class::cast)
                    .forEach(classElement->{
                        //Class<Annotation> annotationClass = (Class<Annotation>) tableAnno.getClass();// annotationForName();
                        /*Annotation annotationObject = classElement.getAnnotation(annotationClass);
                        Method method = TRY(()->annotationClass.getDeclaredMethod("interfaceName"));
                        Object value = TRY(()->method.invoke(annotationObject));
                        log("Got " + method.getName() + "'s value is :" + value);*/
                        
                        stream(classElement.getAnnotationMirrors()).forEach(annoMirror->{
                            annoMirror.getElementValues().forEach((key, value)->{
                                log("当前注解:" + annoMirror.toString(), key.toString(), value.toString());
                            });
                        });
                        
                        log("File access test: current path is " + TRY(()->new File("").getCanonicalFile().toString()));
                        
                        
                        final String packageName = packageName(classElement);
                        final String className = className(classElement);
                        final List<String> fieldsName = stream(classElement.getEnclosedElements())
                                .filter(ele->ele.getKind() == ElementKind.FIELD)
                                .map(e->e.getSimpleName().toString())
                                .collect(toList());
                        writeJavaSourceFile(packageName, className, fieldsName);
                        
                        List<Map<String, String>> fields = stream(classElement.getEnclosedElements())
                                .filter(ele->ele.getKind() == ElementKind.FIELD)
                                .map(VariableElement.class::cast)
                                .map(this::getFieldInfo)
                                .collect(toList());
                        stream(fields).forEach(f->log(f.toString()));
                    });
            
            log("修改一波源文件");
            /*stream(roundEnv.getElementsAnnotatedWith(tableAnno))
                    .filter(element->ElementKind.CLASS == element.getKind())
                    .map(TypeElement.class::cast)
                    .forEach(classElement->{
                        List<Map<String, String>> fields = stream(classElement.getEnclosedElements())
                                .filter(ele->ele.getKind() == ElementKind.FIELD)
                                .map(VariableElement.class::cast)
                                .map(this::getFieldInfo)
                                .map(fieldMap->{
                                    Elements elementUtils = processingEnv.getElementUtils();
                                })
                                .collect(toList());
                        
                        //classElement.getEnclosedElements().addAll();
                    
                    });*/
        });
        return true;
    }
    
    private String packageName(Element type) {
        return processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
    }
    
    private String className(TypeElement type) {
        return type.getSimpleName().toString();
    }
    
    private Map<String, String> getFieldInfo(VariableElement field) {
        Map<String, String> map = new HashMap<>();
        String name = field.getSimpleName().toString();
        String modifiers = stream(field.getModifiers()).map(Objects::toString).collect(joining(" "));
        String type = field.asType().toString();
        String constValue = IF(field.getConstantValue()).map(Object::toString).orElse(null);
        map.put("name", name);
        map.put("modifiers", modifiers);
        map.put("type", type);
        map.put("const_value", constValue);
        return map;
    }
    
    /**
     * write java source file base on you needs
     */
    private void writeJavaSourceFile(String packageName, String className, List<String> fieldsName) {
        final String SP1 = "    ", SP2 = SP1 + SP1, SP3 = SP2 + SP1;
        
        final String classFullyName = packageName + "." + className + "Table";
        try (PrintWriter out = createSourceFileAndGetWriter(classFullyName)) {
            out.println("package " + packageName + ";");
            out.println();
            out.println("public class " + className + "Table {");
            out.println();
            for (String field : fieldsName) {
                out.println(SP1 + "public static final String " + field + " = " + QUOTE(field) + ";");
            }
            out.println();
            out.println("}");
        }
        
        try (PrintWriter out = createResourceFileAndGetWriter(packageName, className + ".xml")) {
            out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            out.println("<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">");
            out.println("<mapper namespace=\"" + classFullyName + "\">");
            out.println(SP1 + "<resultMap id=\"BaseResultMap\" type=\"" + classFullyName + "\">");
            for (String field : fieldsName) {
                out.println(SP2 + "<result column=\"" + convertCamelToUnderscore(field) + "\" property=\"" + field + "\" jdbcType=\"VARCHAR\"/>");
            }
            out.println(SP1 + "</resultMap>");
            out.println("</mapper>");
        }
        
        try (PrintWriter out = createResourceFileAndGetWriter("", "META-INF/services/java.lang.List")) {
            out.println("java.lang.String");
            out.println("java.lang.Object");
        }
        
        log("Created a java source file: " + classFullyName);
    }
    
    private String QUOTE(String str) {
        return "\"" + str + "\"";
    }
    
    private PrintWriter createSourceFileAndGetWriter(String name) {
        try {
            JavaFileObject sourceFile = filer.createSourceFile(name);
            return new PrintWriter(sourceFile.openWriter());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private PrintWriter createResourceFileAndGetWriter(String pkgName, String resourceName) {
        try {
            FileObject resource = filer.createResource(StandardLocation.CLASS_OUTPUT, pkgName, resourceName);
            return new PrintWriter(resource.openWriter());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private <A extends Annotation> Class<A> annotationForName(String className) {
        try {
            return (Class<A>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
    
    
    //####################################################################################
    //# Utils methods
    //####################################################################################
    private void log(String... msg) {
        String allMsg = "TableProcessor: " + stream(msg).collect(joining(" "));
        System.out.println(allMsg);
        messager.printMessage(Diagnostic.Kind.NOTE, allMsg);
    }
    
    private <E> Stream<E> stream(Collection<E> collection) {
        return collection == null ? Stream.empty() : collection.stream();
    }
    
    private <E> Stream<E> stream(E[] array) {
        return array == null ? Stream.empty() : Arrays.stream(array);
    }
    
    private <E extends Object> Optional<E> IF(E chars) {
        if (chars != null) return Optional.of(chars);
        return Optional.empty();
    }
    
    private <E extends CharSequence> Optional<E> IF(E chars) {
        if (chars != null && chars.length() > 0) return Optional.of(chars);
        return Optional.empty();
    }
    
    private <E extends Collection<?>> Optional<E> IF(E collection) {
        if (collection != null && !collection.isEmpty()) return Optional.of(collection);
        return Optional.empty();
    }
    
    private <E extends Boolean, R> Optional<R> IF(E trueValue, R value) {
        if (trueValue != null && trueValue.booleanValue()) return Optional.of(value);
        return Optional.empty();
    }
    
    private <R> R TRY(UncheckedSupplier<R> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private interface UncheckedSupplier<R> {
        R get() throws Exception;
    }
    
    //# About String operations
    public String convertCamelToUnderscore(String input) {
        Pattern pattern = Pattern.compile("(?<=[a-z])[A-Z]");
        Matcher matcher = pattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "_" + matcher.group().toLowerCase());
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
    
}
