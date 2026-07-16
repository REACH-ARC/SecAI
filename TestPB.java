import java.io.IOException;

public class TestPB {
    public static void main(String[] args) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "echo", "hello");
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            int exitCode = process.waitFor();
            System.out.println("cmd /c echo hello: " + exitCode);
        } catch (Exception e) {
            System.out.println("cmd /c echo hello: FAILED - " + e.getMessage());
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-version");
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            int exitCode = process.waitFor();
            System.out.println("java -version: " + exitCode);
        } catch (Exception e) {
            System.out.println("java -version: FAILED - " + e.getMessage());
        }
    }
}
