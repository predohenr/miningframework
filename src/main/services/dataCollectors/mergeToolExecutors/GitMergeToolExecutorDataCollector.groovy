package services.dataCollectors.mergeToolExecutors

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Arrays
import java.util.List

class GitMergeToolExecutorDataCollector extends BaseMergeToolExecutorDataCollector {

    GitMergeToolExecutorDataCollector(String extension) {
        super(extension)
    }

    @Override
    protected List<String> getArgumentsForTool(Path file, Path outputFile) {
        String basePath = file.resolve("base" + this.extension).toAbsolutePath().toString()
        String rightPath = file.resolve("right" + this.extension).toAbsolutePath().toString()
        
        try {
            Files.copy(
                file.resolve("left" + this.extension), 
                outputFile, 
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (Exception e) {
            throw new RuntimeException("Falha ao preparar arquivo para o diff3: " + e.getMessage(), e)
        }

        return Arrays.asList(
                "git", 
                "merge-file", 
                outputFile.toAbsolutePath().toString(), 
                basePath, 
                rightPath
        )
    }

    @Override
    String getToolName() {
        return "diff3"
    }
}