package services.dataCollectors.mergeToolExecutors

import java.nio.file.Path
import java.nio.file.Paths

class S3MMergeToolExecutorDataCollector extends BaseMergeToolExecutorDataCollector {

    S3MMergeToolExecutorDataCollector(String extension) {
        super(extension)
    }

    @Override
    String getToolName() {
        return "s3m"
    }

    @Override
    protected List<String> getArgumentsForTool(Path scenarioDirectory, Path outputFile) {
        Path leftFile = scenarioDirectory.resolve("left" + this.extension).toAbsolutePath()
        Path baseFile = scenarioDirectory.resolve("base" + this.extension).toAbsolutePath()
        Path rightFile = scenarioDirectory.resolve("right" + this.extension).toAbsolutePath()
        
        Path s3mJarPath = Paths.get("dependencies/s3m-all.jar").toAbsolutePath()

        return [
            "java", 
            "-jar", 
            s3mJarPath.toString(),
            leftFile.toString(),
            baseFile.toString(),
            rightFile.toString(),
            "-o", outputFile.toString(),
            "-c", "false", 
            "-l", "false"
        ]
    }
}