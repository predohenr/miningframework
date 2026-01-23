package services.dataCollectors.mergeToolExecutors

import java.nio.file.Path

class MergirafSemiCMergeToolExecutorDataCollector extends BaseMergeToolExecutorDataCollector {
    private static String MERGIRAF_PATH = "./dependencies/mergiraf-semi-c"

    @Override
    protected List<String> getArgumentsForTool(Path file, Path outputFile) {
        return Arrays.asList(MERGIRAF_PATH, "merge", "--semistructured=diff3",
                file.resolve("base.java").toAbsolutePath().toString(),
                file.resolve("left.java").toAbsolutePath().toString(),
                file.resolve("right.java").toAbsolutePath().toString(),
                "-o", outputFile.toAbsolutePath().toString())
    }

    @Override
    String getToolName() { return "mergiraf_semi_c" }
}