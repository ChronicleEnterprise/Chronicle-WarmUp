package software.chronicle.enterprise.warmup;

import org.junit.Test;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by peter on 24/06/15.
 */
public class WarmupTest {

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
        warmup.compileClass(ConcurrentHashMap.class);
        //warmup.dump((m, l) -> System.out.println(m + " => " + l));
        long start = System.currentTimeMillis();
        warmup.start();
        warmup.waitFor();
        long end = System.currentTimeMillis() - start;
        Thread.sleep(10000);
        System.out.println("Took " + end / 1e3 + " seconds.");

    }
}