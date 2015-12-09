package software.chronicle.enterprise.warmup;

import java.lang.reflect.Executable;

/**
 * Created by plawrey on 12/9/15.
 */
public enum NoWhiteBox implements IWhiteBox {
    INSTANCE {
        @Override
        public void deoptimizeMethod(Executable executable) {

        }

        @Override
        public int getCompileQueuesSize() {
            return 0;
        }

        @Override
        public int getMethodEntryBci(Executable executable) {
            return 0;
        }

        @Override
        public void enqueueMethodForCompilation(Executable executable, int value, int bci) {

        }

        @Override
        public boolean isMethodCompilable(Executable executable) {
            return false;
        }

        @Override
        public boolean isMethodCompiled(Executable executable) {
            return false;
        }
    };
}
