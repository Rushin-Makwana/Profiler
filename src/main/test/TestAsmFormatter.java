import com.ag.profiler.bytebuddy.jar.asm.Type;

public class TestAsmFormatter {
    public static void main(String[] args) {
        String desc = "([Ljava/lang/String;[[I)V";
        Type[] argTypes = Type.getArgumentTypes(desc);
        for (Type t : argTypes) {
            System.out.println(t.getClassName());
        }
    }
}
