package services.dataCollectors.mergeToolExecutors

import java.nio.file.Path
import util.ProcessRunner

class GitMergeExecutor extends BaseMergeToolExecutorDataCollector {

    @Override
    protected List<String> getArgumentsForTool(Path file, Path outputFile) {
        return Arrays.asList("git", 
                "merge-file",
                "-p", 
                file.resolve("left.java").toAbsolutePath().toString(),
                file.resolve("base.java").toAbsolutePath().toString(),
                file.resolve("right.java").toAbsolutePath().toString())
    }

    @Override
    protected void executeTool(Path file, Path outputFile) {
        def processBuilder = new ProcessBuilder(getArgumentsForTool(file, outputFile))
        
        processBuilder.redirectOutput(outputFile.toFile())
        
        def process = processBuilder.start()
        process.waitFor()
    }

    @Override
    String getToolName() {
        return "diff3"
    }
}