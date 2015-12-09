package software.chronicle.enterprise.warmup;

import sun.hotspot.WhiteBox;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
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
    public static final int HIGH_COMP_LEVEL = 3;
    public static final int MAX_COMP_LEVEL = 4;
    public static final IWhiteBox WHITE_BOX = IWhiteBox.instance();
    private static final Logger LOGGER = Logger.getLogger(Warmup.class.getName());
    private static final ClassValue<AtomicBoolean> CLASS_COMPILED = new ClassValue<AtomicBoolean>() {
        @Override
        protected AtomicBoolean computeValue(Class<?> type) {
            return new AtomicBoolean();
        }
    };

    static {
        CLASS_COMPILED.get(WhiteBox.class).set(true);
        CLASS_COMPILED.get(Warmup.class).set(true);
        if (WHITE_BOX == NoWhiteBox.INSTANCE) {
            LOGGER.warning("WhiteBox not loaded. Make sure it is on the -Xbootclasspath/a:Chronicle-WhiteBox.jar");
        }
    }

    private final Map<Executable, Integer> methodToCompLevelMap;

    public Warmup() {
        this(new LinkedHashMap<>());
    }

    private Warmup(Map<Executable, Integer> methodToCompLevelMap) {
        this.methodToCompLevelMap = methodToCompLevelMap;
    }

    public static boolean isEnabled() {
        return WHITE_BOX instanceof RealWhiteBox;
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
        Method waitFor = getEOTCQMethod();
        WHITE_BOX.deoptimizeMethod(waitFor);
        for (Map.Entry<Executable, Integer> entry : methodToCompLevelMap.entrySet()) {
//            System.out.println(entry.getKey()+" =>" + entry.getValue());
            if (WHITE_BOX.getCompileQueuesSize() > 128)
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            Executable executable = entry.getKey();
            int bci = WHITE_BOX.getMethodEntryBci(executable);
            WHITE_BOX.enqueueMethodForCompilation(executable, entry.getValue(), bci);
        }
    }

    public void compileForInstance(Object o) {
        compileForInstance(o, HIGH_COMP_LEVEL);
    }

    public void compileForInstance(Object o, int compLevel) {
        compileForInstance(o, compLevel, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private void compileForInstance(Object o, int compLevel, Set<Object> objs) {
        if (o == null || !objs.add(o)) return;
        compileClass(o.getClass(), compLevel);

        for (Class oClass = o.getClass(); oClass != null; oClass = oClass.getSuperclass()) {
            for (Field field : o.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    if (field.getType().isPrimitive())
                        continue;
                    Object fieldValue = field.get(o);
                    if (fieldValue != null)
                        compileForInstance(fieldValue, compLevel, objs);
                } catch (IllegalAccessException e) {
                    // ignored
                }
            }
        }
    }

    public void compileClass(Class clazz) {
        compileClass(clazz, HIGH_COMP_LEVEL);
    }

    public void compileClass(Class clazz, int compLevel) {
        if (CLASS_COMPILED.get(clazz).getAndSet(true))
            return;
        Class superclass = clazz.getSuperclass();
        if (superclass != null)
            compileClass(clazz, compLevel);
        for (Class iClass : clazz.getInterfaces())
            compileClass(iClass, compLevel);
        try {
            for (Constructor constructor : clazz.getDeclaredConstructors()) {
                if (WHITE_BOX.isMethodCompilable(constructor))
                    methodToCompLevelMap.put(constructor, compLevel);
                compileExecutable(constructor, compLevel);
            }
        } catch (Throwable e) {
            LOGGER.warning("Failed to get constructors for " + clazz + " " + e);
        }
        try {
            for (Method method : clazz.getDeclaredMethods()) {
                if ((method.getModifiers() & Modifier.NATIVE) == 0 && WHITE_BOX.isMethodCompilable(method))
                    methodToCompLevelMap.put(method, compLevel);

                for (Class aClass : method.getParameterTypes()) {
                    compileClass(aClass, compLevel);
                }
            }
        } catch (Throwable e) {
            LOGGER.warning("Failed to get methods for " + clazz + " " + e);
        }
        try {
            for (Field field : clazz.getDeclaredFields()) {
                Class<?> type = field.getType();
                compileClass(type, compLevel);
            }
        } catch (Throwable e) {
            LOGGER.warning("Failed to get fields for " + clazz + " " + e);
        }
    }

    private void compileExecutable(Executable executable, int compLevel) {
        for (Class aClass : executable.getExceptionTypes()) {
            compileClass(aClass, compLevel);
        }
        for (Class aClass : executable.getParameterTypes()) {
            compileClass(aClass, compLevel);
        }
        if (executable instanceof Method)
            compileClass(((Method) executable).getReturnType(), compLevel);
    }

    public void waitFor() {
        Method waitFor = getEOTCQMethod();
        WHITE_BOX.enqueueMethodForCompilation(waitFor, 1, -1);
        for (int i = 0; i < 200; i++)
            if (waitFor0(waitFor))
                break;
    }

    private Method getEOTCQMethod() {
        Method waitFor;
        try {
            waitFor = Warmup.class.getDeclaredMethod("endOfTheCompilationQueue");
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
        assert WHITE_BOX.isMethodCompilable(waitFor) || WHITE_BOX == NoWhiteBox.INSTANCE;
        return waitFor;
    }

    private boolean waitFor0(Method waitFor) {
        try {
            if (WHITE_BOX.isMethodCompiled(waitFor) || !WHITE_BOX.isMethodCompilable(waitFor)) {
                return true;
            }
            Thread.sleep(10);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    void endOfTheCompilationQueue() {
        // dummy method so we know we have finished compiling.
    }
}
