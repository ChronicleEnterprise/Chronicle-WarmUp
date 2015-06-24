package software.chronicle.enterprise.warmup;

import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by peter on 24/06/15.
 */
public class PrintCompilation {
    public final long millis;
    public final int id;
    public final Set<Character> codes = new TreeSet<>();
    public final int compLevel;
    public final String methodName;
    public final String state;

    public PrintCompilation(String line) {
        line = line.trim();
        Scanner scan = new Scanner(line);
        millis = scan.nextLong();
        id = scan.nextInt();
        while (!scan.hasNextInt())
            for (char ch : scan.next().toCharArray()) {
                codes.add(ch);
            }
        compLevel = scan.nextInt();
        methodName = scan.next();
        String state;
        try {
            while (scan.hasNext()) {
                String next = scan.next();
                if (next.startsWith("(") || next.startsWith(")"))
                    continue;
                break;
            }
            state = scan.nextLine();
            if (state != null)
                state = state.trim();
        } catch (Exception e) {
            state = null;
        }
        this.state = state;
    }

    @Override
    public String toString() {
        return "PrintCompilation{" +
                "millis=" + millis +
                ", id=" + id +
                ", codes=" + codes +
                ", compLevel=" + compLevel +
                ", methodName='" + methodName + '\'' +
                ", state='" + state + '\'' +
                '}';
    }
}
