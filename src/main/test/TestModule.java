import net.bytebuddy.dynamic.ClassFileLocator;
public class TestModule {
    public static void test() {
        ClassFileLocator loc = ClassFileLocator.ForModule.ofBootLayer();
    }
}
