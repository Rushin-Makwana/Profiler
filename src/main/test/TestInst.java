import net.bytebuddy.dynamic.ClassFileLocator;
import java.lang.instrument.Instrumentation;
public class TestInst {
    public static void test(Instrumentation inst) {
        ClassFileLocator loc = ClassFileLocator.ForInstrumentation.of(inst, TestInst.class);
    }
}
