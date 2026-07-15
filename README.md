# SecAI

SecAI is a cross-platform, AI-powered security analysis CLI tool. It acts as an intelligent abstraction layer over industry-standard security scanners (Semgrep, Trivy), enriching their findings with AI-generated explanations, attack scenarios, remediation steps, and secure code examples.

## Features
- **Multi-Scanner Architecture**: Seamlessly aggregates findings from Semgrep and Trivy.
- **Provider Agnostic**: Bring your own AI. Supports **OpenAI**, **Gemini**, and **Ollama** out of the box.
- **Contextual Memory**: Maintain an interactive chat session with the AI about specific vulnerabilities.
- **Self-Healing**: Safely generate and apply `.fixed` remediation files next to vulnerable code.
- **Premium Reporting**: Export findings into shareable Markdown or stunning HTML formats.

## Installation

SecAI distributes pre-compiled native binaries so you don't even need Java installed to run it!

**Linux & macOS:**
```bash
curl -sSL https://raw.githubusercontent.com/REACH-ARC/SecAI/main/install.sh | bash
```

**Windows (PowerShell):**
```powershell
irm https://raw.githubusercontent.com/REACH-ARC/SecAI/main/install.ps1 | iex
```

### Prerequisites (for scanners)
While SecAI doesn't require Java, it does depend on the underlying scanners being installed in your system PATH:
- [Semgrep](https://semgrep.dev/) *(Note: For Windows, you can install via WSL or Python `pip install semgrep`. If unavailable, SecAI will gracefully skip it and rely on Trivy!)*
- [Trivy](https://trivy.dev/)

## Getting Started

### 1. Configuration
SecAI uses a `secai-config.yaml` file in the root of your target project.
```yaml
provider: openai
openai:
  apiKey: "YOUR_API_KEY"
  model: "gpt-4-turbo"
# gemini:
#   apiKey: "YOUR_API_KEY"
# ollama:
#   url: "http://localhost:11434"
```

### 3. Usage

Run SecAI from your terminal:

**Configure AI Provider:**
```bash
secai config --provider openai --api-key "YOUR_KEY" --model "gpt-4-turbo"
```

**Scan a project:**
```bash
secai scan .
```

**Explain a finding:**
```bash
secai explain 1
```

**Get remediation steps & safe auto-fix:**
```bash
secai fix 1 --apply
```

**Chat interactively with the AI about a finding:**
```bash
secai chat 1
```

**Generate a report (Markdown or HTML):**
```bash
secai report --format html --output report.html
```

**Update scanner vulnerability databases:**
```bash
secai update
```

## Distributing as a Native Executable (GraalVM)

Because SecAI is a command-line tool, you'll want it to start instantly. You can compile it into a standalone native binary (no JVM required) using **GraalVM Native Image**.

1. Install [GraalVM](https://www.graalvm.org/) (Java 21 version) and set it as your `JAVA_HOME`.
2. Ensure you have the `native-image` tool installed.
3. Because we use Spring Boot 3, native compilation is built-in. Run:
```bash
mvn -Pnative native:compile
```
4. This will output a native executable in the `target/` directory (e.g., `secai.exe` on Windows or `secai` on Linux/macOS).
5. You can now move this binary to your system PATH and run it instantly: `secai scan .`
