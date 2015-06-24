package software.chronicle.enterprise.warmup;

import sun.hotspot.WhiteBox;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
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
    private final Map<String, Integer> methodToCompLevelMap;

    public Warmup(Map<String, Integer> methodToCompLevelMap) {
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
        final Map<String, Integer> methodToCompLevelMap = new LinkedHashMap<>();
        for (PrintCompilation pc : pcs.values()) {
            if (pc == null || pc.state != null) continue;
            methodToCompLevelMap.put(pc.methodName, pc.compLevel);
        }
        return new Warmup(methodToCompLevelMap);
    }

    public void dump(BiConsumer<String, Integer> consumer) {
        for (Map.Entry<String, Integer> entry : methodToCompLevelMap.entrySet()) {
            consumer.accept(entry.getKey(), entry.getValue());
        }
    }

    public void start() {
        Pattern pattern = Pattern.compile("::");
        for (Map.Entry<String, Integer> entry : methodToCompLevelMap.entrySet()) {
            String[] part = pattern.split(entry.getKey(), 2);
            try {
                Class<?> aClass = Class.forName(part[0]);
                for (Method method : aClass.getDeclaredMethods()) {
                    if (method.getName().equals(part[1])) {
                        System.out.println(method + ", level=" + entry.getValue());
                        WHITE_BOX.enqueueMethodForCompilation(method, entry.getValue());
                    }
                }
            } catch (ClassNotFoundException e) {
                System.out.println("skipping " + part[0]);
            }
        }
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

    }
}
