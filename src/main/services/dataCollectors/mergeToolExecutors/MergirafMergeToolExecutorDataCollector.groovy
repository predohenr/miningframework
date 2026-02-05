package services.dataCollectors.mergeToolExecutors

import java.nio.file.Path
import java.util.Arrays
import java.util.List

class MergirafMergeToolExecutorDataCollector extends BaseMergeToolExecutorDataCollector {
    private static String MERGIRAF_PATH = "./dependencies/mergiraf"

    MergirafMergeToolExecutorDataCollector(String extension) {
        super(extension)
    }

    @Override
    protected List<String> getArgumentsForTool(Path file, Path outputFile) {
        return Arrays.asList(MERGIRAF_PATH,
                "merge",
                file.resolve("base" + this.extension).toAbsolutePath().toString(),
                file.resolve("left" + this.extension).toAbsolutePath().toString(),
                file.resolve("right" + this.extension).toAbsolutePath().toString(),
                "--output=${outputFile.toAbsolutePath().toString()}".toString())
    }

    @Override
    String getToolName() {
        return "mergiraf"
    }
}