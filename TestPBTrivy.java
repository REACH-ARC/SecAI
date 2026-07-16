import java.io.IOException;

public class TestPBTrivy {
    public static void main(String[] args) {
        try {
            ProcessBuilder pb = new ProcessBuilder("trivy", "--version");
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            int exitCode = process.waitFor();
            System.out.println("trivy --version: " + exitCode);
        } catch (Exception e) {
            System.out.println("trivy --version: FAILED - " + e.getMessage());
        }
    }
}
