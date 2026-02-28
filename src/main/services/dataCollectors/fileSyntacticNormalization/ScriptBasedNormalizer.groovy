package services.dataCollectors.fileSyntacticNormalization

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class ScriptBasedNormalizer {

    private static final String SCRIPTS_DIR = "src/main/resources/normalizationScripts"

    static String normalize(String content, String extension) {
        return simpleNormalize(content)

        // --- CÓDIGO ANTIGO (Preservado para futuro) ---
        /*
        if (!["py", "js", "ts", "go", "rs"].contains(extension)) {
            return content
        }

        Path tempFile = Files.createTempFile("mining_norm_", "." + extension)
        Files.write(tempFile, content.getBytes(), StandardOpenOption.WRITE)

        try {
            String command = buildCommand(extension, tempFile.toAbsolutePath().toString())
            
            def process = command.execute()
            def stdout = new StringBuilder()
            def stderr = new StringBuilder()

            process.waitForProcessOutput(stdout, stderr)

            if (process.exitValue() == 0) {
                return stdout.toString()
            } else {
                System.err.println("Erro ao normalizar .${extension}: " + stderr.toString())
                return content
            }

        } catch (Exception e) {
            e.printStackTrace()
            return content
        } finally {
            Files.deleteIfExists(tempFile)
        }
        */
    }

    private static String simpleNormalize(String content) {
        if (content == null) return ""
        // regex \s+ removes spaces, tabs (\t), linebreakers (\n, \r)
        return content.replaceAll("\\s+", "")
    }

    /* Método antigo
    private static String buildCommand(String extension, String filePath) {
        switch (extension) {
            case "py":
                return "python3 ${SCRIPTS_DIR}/normalize_python.py ${filePath}"
            
            case "js":
            case "ts":
                return "node ${SCRIPTS_DIR}/normalize_js.js ${filePath}"
            
            case "go":
                return "go run ${SCRIPTS_DIR}/normalize_go.go ${filePath}"
            
            case "rs":
                return "cargo run --manifest-path ${SCRIPTS_DIR}/rust_normalizer/Cargo.toml --quiet -- ${filePath}"
            
            default:
                throw new IllegalArgumentException("Extensão não suportada: " + extension)
        }
    }
    */
}