package services.dataCollectors.mergeToolExecutors

import java.nio.file.Path
import java.util.Arrays
import java.util.List

class MergirafSemiCMergeToolExecutorDataCollector extends BaseMergeToolExecutorDataCollector {
    private static String MERGIRAF_PATH = "./dependencies/mergiraf-semi-c"

    MergirafSemiCMergeToolExecutorDataCollector(String extension) {
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
    protected Map<String, String> parseLogMetrics(File logFile) {
        Map<String, String> metrics = [
            "phase1_time": "",
            "phase2_time": "",
            "phase2_unify_time": "",
            "phase2_diffy_calls": "",
            "phase2_diffy_time": "",
            "phase3_time": "",
            "phase3_unify_time": "",
            "phase3_diffy_calls": "",
            "phase3_diffy_time": "",
            "total_merge_module_time": ""
        ]

        if (!logFile.exists()) {
            return metrics
        }

        int currentPhase = 1 

        logFile.eachLine { line ->
            def p1 = line =~ /(?i)Phase 1.*?took:\s*([0-9.]+[msnµμ]+)/
            if (p1.find()) { 
                metrics["phase1_time"] = p1.group(1)
                currentPhase = 2 
            }

            def p2 = line =~ /(?i)Phase 2.*?took:\s*([0-9.]+[msnµμ]+)/
            if (p2.find()) { 
                metrics["phase2_time"] = p2.group(1)
                currentPhase = 3 
            }

            def p3 = line =~ /(?i)Phase 3.*?took:\s*([0-9.]+[msnµμ]+)/
            if (p3.find()) { 
                metrics["phase3_time"] = p3.group(1) 
            }

            def unify = line =~ /(?i)Unify Concurrent Additions time:\s*([0-9.]+[msnµμ]+)/
            if (unify.find()) {
                if (currentPhase == 2) metrics["phase2_unify_time"] = unify.group(1)
                else if (currentPhase == 3) metrics["phase3_unify_time"] = unify.group(1)
            }

            def diffy = line =~ /(?i)DIFFY CALLS:\s*([0-9]+)/
            if (diffy.find()) {
                if (currentPhase == 2) metrics["phase2_diffy_calls"] = diffy.group(1)
                else if (currentPhase == 3) metrics["phase3_diffy_calls"] = diffy.group(1)
            }

            def diffyTime = line =~ /(?i)TIME SPENT ON DIFFY:\s*([0-9.]+[msnµμ]+)/
            if (diffyTime.find()) {
                if (currentPhase == 2) metrics["phase2_diffy_time"] = diffyTime.group(1)
                else if (currentPhase == 3) metrics["phase3_diffy_time"] = diffyTime.group(1)
            }

            def totalMain = line =~ /(?i)Total time merge module:\s*([0-9.]+[msnµμ]+)/
            if (totalMain.find()) metrics["total_merge_module_time"] = totalMain.group(1)
        }
        return metrics
    }

    @Override
    String getToolName() { 
        return "mergiraf_semi" 
    }
}