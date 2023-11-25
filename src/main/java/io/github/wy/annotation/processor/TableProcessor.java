package io.github.wy.annotation.processor;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
            stream(roundEnv.getElementsAnnotatedWith(tableAnno))
                    .filter(element->ElementKind.CLASS == element.getKind())
                    .map(e->(TypeElement) e)
                    .forEach(classElement->{
                        final String packageName = packageName(classElement);
                        final String className = className(classElement);
                        final List<String> fieldsName = stream(classElement.getEnclosedElements())
                                .filter(ele->ele.getKind() == ElementKind.FIELD)
                                .map(e->e.getSimpleName().toString())
                                .collect(toList());
                        writeJavaSourceFile(packageName, className, fieldsName);
                    });
        });
        return true;
    }
    
    private String packageName(Element type) {
        return processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
    }
    
    private String className(TypeElement type) {
        return type.getSimpleName().toString();
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
    
    
    //####################################################################################
    //# Utils methods
    //####################################################################################
    private void log(String... msg) {
        String allMsg = "TableProcessor: " + stream(msg).collect(Collectors.joining(" "));
        System.out.println(allMsg);
        messager.printMessage(Diagnostic.Kind.NOTE, allMsg);
    }
    
    private <E> Stream<E> stream(Collection<E> collection) {
        return collection == null ? Stream.empty() : collection.stream();
    }
    
    private <E> Stream<E> stream(E[] array) {
        return array == null ? Stream.empty() : Arrays.stream(array);
    }
    
    private <E extends CharSequence> Optional<E> IF(E chars) {
        if (chars != null && chars.length() > 0) return Optional.of(chars);
        return Optional.empty();
    }
    
    private <E extends Collection<?>> Optional<E> IF(E collection) {
        if (collection != null && collection.size() > 0) return Optional.of(collection);
        return Optional.empty();
    }
    
    private <E extends Boolean, R> Optional<R> IF(E trueValue, R value) {
        if (trueValue != null && trueValue.booleanValue()) return Optional.of(value);
        return Optional.empty();
    }
    
}
