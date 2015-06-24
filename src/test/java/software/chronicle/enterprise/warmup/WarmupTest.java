package software.chronicle.enterprise.warmup;

import org.junit.Test;

import java.io.File;

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
}