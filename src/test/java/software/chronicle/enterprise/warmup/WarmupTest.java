package software.chronicle.enterprise.warmup;

import org.junit.Test;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by peter on 24/06/15.
 */
/*
        -ea
        -XX:+UnlockDiagnosticVMOptions
        -XX:+WhiteBoxAPI
        -Xbootclasspath/a:${user.home}/git/Chronicle-WarmUp/target/classes
        -XX:+PrintCompilation
*/
public class WarmupTest {

    public static void main(String[] args) throws InterruptedException {
        new WarmupTest().testCompileClass();
    }

    @Test
    public void testCompileFromFile() throws Exception {
        Warmup warmup = Warmup.compileFromFile(new File(Warmup.class.getResource("/print-compilation.txt").getFile()));
        warmup.dump((m, l) -> System.out.println(m + " => " + l));
        warmup.start();
        warmup.waitFor();
        System.out.println("waiting");
        Thread.sleep(1000);
    }

    @Test
    public void testCompileClass() throws InterruptedException {
        Warmup warmup = new Warmup();
        Map<Object, Object> map = new LinkedHashMap<>();
        map.put("compilation", ManagementFactory.getCompilationMXBean());

        warmup.compileForInstance(map, Warmup.MAX_COMP_LEVEL);
//        warmup.dump((m, l) -> System.out.println(m + " => " + l));
        long start = System.currentTimeMillis();
        warmup.start();
        warmup.waitFor();
        long end = System.currentTimeMillis() - start;
        System.out.println("Took " + end / 1e3 + " seconds.");
    }
}