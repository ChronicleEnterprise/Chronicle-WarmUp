package software.chronicle.enterprise.warmup;

import sun.hotspot.WhiteBox;

import java.lang.reflect.Executable;

/**
 * Created by plawrey on 12/9/15.
 */
public enum RealWhiteBox implements IWhiteBox {
    INSTANCE;
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();


    @Override
    public void deoptimizeMethod(Executable executable) {
        WHITE_BOX.deoptimizeMethod(executable);
    }

    @Override
    public int getCompileQueuesSize() {
        return WHITE_BOX.getCompileQueuesSize();
    }

    @Override
    public int getMethodEntryBci(Executable executable) {
        return WHITE_BOX.getMethodEntryBci(executable);
    }

    @Override
    public void enqueueMethodForCompilation(Executable executable, int value, int bci) {
        WHITE_BOX.enqueueMethodForCompilation(executable, value, bci);
    }

    @Override
    public boolean isMethodCompilable(Executable executable) {
        return WHITE_BOX.isMethodCompilable(executable);
    }

    @Override
    public boolean isMethodCompiled(Executable executable) {
        return WHITE_BOX.isMethodCompilable(executable);
    }
}
