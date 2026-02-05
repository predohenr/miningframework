package services.dataCollectors.mergeToolExecutors

import java.nio.file.Path
import java.util.Arrays
import java.util.List

class MergirafSemiSCMergeToolExecutorDataCollector extends BaseMergeToolExecutorDataCollector {
    private static String MERGIRAF_PATH = "./dependencies/mergiraf-semi-sc"

    MergirafSemiSCMergeToolExecutorDataCollector(String extension) {
        super(extension)
    }

    @Override
    protected List<String> getArgumentsForTool(Path file, Path outputFile) {
        return Arrays.asList(MERGIRAF_PATH, "merge", "--semistructured=diff3",
                file.resolve("base" + this.extension).toAbsolutePath().toString(),
                file.resolve("left" + this.extension).toAbsolutePath().toString(),
                file.resolve("right" + this.extension).toAbsolutePath().toString(),
                "-o", outputFile.toAbsolutePath().toString())
    }

    @Override
    String getToolName() { 
        return "mergiraf_semi_sc" 
    }
}