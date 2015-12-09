package software.chronicle.enterprise.warmup;

import java.lang.reflect.Executable;

/**
 * Created by plawrey on 12/9/15.
 */
public interface IWhiteBox {
    static IWhiteBox instance() {
        try {
            return RealWhiteBox.INSTANCE;
        } catch (Throwable e) {
            return NoWhiteBox.INSTANCE;
        }
    }

    void deoptimizeMethod(Executable executable);

    int getCompileQueuesSize();

    int getMethodEntryBci(Executable executable);

    void enqueueMethodForCompilation(Executable executable, int value, int bci);

    boolean isMethodCompilable(Executable executable);

    boolean isMethodCompiled(Executable executable);
}
