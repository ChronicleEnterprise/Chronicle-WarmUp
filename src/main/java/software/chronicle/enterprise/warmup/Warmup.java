package software.chronicle.enterprise.warmup;

import sun.hotspot.WhiteBox;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/**
 * Created by peter on 24/06/15.
 */
/*
 -XX:+UnlockDiagnosticVMOptions
 -XX:+WhiteBoxAPI
 -Xbootclasspath/p:/home/peter/git/Chronicle-WarmUp/target/classes
 */
public class Warmup {
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    private static final int MAX_COMP_LEVEL = 4;
    private static final ClassValue<AtomicBoolean> CLASS_COMPILED = new ClassValue<AtomicBoolean>() {
        @Override
        protected AtomicBoolean computeValue(Class<?> type) {
            return new AtomicBoolean();
        }
    };

    static {
        CLASS_COMPILED.get(WhiteBox.class).set(true);
        CLASS_COMPILED.get(Warmup.class).set(true);
    }

    private final Map<Executable, Integer> methodToCompLevelMap;

    public Warmup() {
        this.methodToCompLevelMap = new LinkedHashMap<>();
    }

    private Warmup(Map<Executable, Integer> methodToCompLevelMap) {
        this.methodToCompLevelMap = methodToCompLevelMap;
    }

    public static Warmup compileFromFile(File file) throws IOException {
        Map<Integer, PrintCompilation> pcs = new TreeMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            for (String line; (line = br.readLine()) != null; ) {
                PrintCompilation pc = new PrintCompilation(line);
                pcs.put(pc.id, pc);
            }
        }
        final Map<String, Integer> methodNameToCompLevelMap = new LinkedHashMap<>();
        for (PrintCompilation pc : pcs.values()) {
            if (pc == null || pc.state != null) continue;
            methodNameToCompLevelMap.put(pc.methodName, pc.compLevel);
        }
        Pattern pattern = Pattern.compile("::");
        Map<Executable, Integer> methodToCompLevelMap = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : methodNameToCompLevelMap.entrySet()) {
            String[] part = pattern.split(entry.getKey(), 2);
            try {
                Class<?> aClass = Class.forName(part[0]);
                if (part[1].equals("<init>")) {
                    for (Constructor constructor : aClass.getDeclaredConstructors())
                        methodToCompLevelMap.putIfAbsent(constructor, entry.getValue());
                } else {
                    for (Method method : aClass.getDeclaredMethods()) {
                        if (method.getName().equals(part[1])) {
                            methodToCompLevelMap.putIfAbsent(method, entry.getValue());
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                System.out.println("skipping " + part[0]);
            }
        }
        return new Warmup(methodToCompLevelMap);
    }

    public void dump(BiConsumer<Executable, Integer> consumer) {
        for (Map.Entry<Executable, Integer> entry : methodToCompLevelMap.entrySet()) {
            consumer.accept(entry.getKey(), entry.getValue());
        }
    }

    public void start() {
        for (Map.Entry<Executable, Integer> entry : methodToCompLevelMap.entrySet()) {
            WHITE_BOX.enqueueMethodForCompilation(entry.getKey(), entry.getValue());
        }
    }

    public void compileClass(Class clazz) {
        if (CLASS_COMPILED.get(clazz).getAndSet(true))
            return;
        Class superclass = clazz.getSuperclass();
        if (superclass != null)
            compileClass(clazz);
        for (Constructor constructor : clazz.getDeclaredConstructors()) {
            methodToCompLevelMap.put(constructor, MAX_COMP_LEVEL);
            compileExecutable(constructor);
        }
        for (Method method : clazz.getDeclaredMethods()) {
            if ((method.getModifiers() & (Modifier.ABSTRACT | Modifier.NATIVE)) == 0) {
                methodToCompLevelMap.put(method, MAX_COMP_LEVEL);
            }
            for (Class aClass : method.getParameterTypes()) {
                compileClass(aClass);
            }
        }
        for (Field field : clazz.getDeclaredFields()) {
            Class<?> type = field.getType();
            compileClass(type);
        }
    }

    private void compileExecutable(Executable executable) {
        for (Class aClass : executable.getExceptionTypes()) {
            compileClass(aClass);
        }
        for (Class aClass : executable.getParameterTypes()) {
            compileClass(aClass);
        }
        if (executable instanceof Method)
            compileClass(((Method) executable).getReturnType());
    }

    public void waitFor() {
        Method waitFor;
        try {
            waitFor = Warmup.class.getDeclaredMethod("endOfTheCompilationQueue");
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
        WHITE_BOX.deoptimizeMethod(waitFor);
        WHITE_BOX.enqueueMethodForCompilation(waitFor, 3);
        try {
            while (!WHITE_BOX.isMethodCompiled(waitFor))
                Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void endOfTheCompilationQueue() {
        // dummy method so we know we have finished compiling.
    }
}
