package services.dataCollectors.mergeToolExecutors

import interfaces.DataCollector
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import project.MergeCommit
import project.Project
import services.dataCollectors.S3MMergesCollector.MergeScenarioCollector
import services.dataCollectors.mergeToolExecutors.model.MergeExecutionResult
import services.dataCollectors.mergeToolExecutors.model.MergeExecutionSummary
import services.util.MergeConflict
import util.CsvUtils
import util.ProcessRunner

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

import static app.MiningFramework.arguments

abstract class BaseMergeToolExecutorDataCollector implements DataCollector {
    private static Logger LOG = LogManager.getLogger(BaseMergeToolExecutorDataCollector.class)

    protected static PERF_SAMPLING_TOTAL_NUMBER_OF_EXECUTIONS = 6
    protected static TIMEOUT_IN_MINUTES = 30
    
    protected final String extension

    BaseMergeToolExecutorDataCollector(String extension) {
        String raw = extension ?: ".java"
        this.extension = raw.startsWith(".") ? raw : "." + raw
    }

    @Override
    void collectData(Project project, MergeCommit mergeCommit) {
        def scenarioFiles = MergeScenarioCollector.collectNonFastForwardMergeScenarios(project, mergeCommit)
        if (scenarioFiles.isEmpty()) {
            LOG.debug("Early returning because there are no mutually modified files")
            return
        }

        def summaries = scenarioFiles.stream()
                .map(this::runMergeForFile)
                .map(summary -> [
                        project.getName(), 
                        mergeCommit.getSHA(), 
                        summary.file, 
                        summary.output, 
                        summary.result, 
                        summary.time,
                        summary.extraMetrics.getOrDefault("phase1_time", ""),
                        summary.extraMetrics.getOrDefault("phase2_time", ""),
                        summary.extraMetrics.getOrDefault("phase2_unify_time", ""),
                        summary.extraMetrics.getOrDefault("phase2_diffy_calls", ""),
                        summary.extraMetrics.getOrDefault("phase2_diffy_time", ""),
                        summary.extraMetrics.getOrDefault("phase3_time", ""),
                        summary.extraMetrics.getOrDefault("phase3_unify_time", ""),
                        summary.extraMetrics.getOrDefault("phase3_diffy_calls", ""),
                        summary.extraMetrics.getOrDefault("phase3_diffy_time", ""),
                        summary.extraMetrics.getOrDefault("total_merge_module_time", "")
                ])
                .map(CsvUtils::toCsvRepresentation)
                .collect(Collectors.toList())

        writeReportToFile(arguments.getOutputPath() + "/reports/merge-tools/${getToolName()}.csv", summaries)
    }

    protected static synchronized writeReportToFile(String reportFilePath, List<String> lines) {
        def reportFile = new File(reportFilePath)
        Files.createDirectories(Paths.get(arguments.getOutputPath() + "/reports/merge-tools/"))
        reportFile.createNewFile()
        reportFile << lines.stream().collect(CsvUtils::asLines()) << System.lineSeparator()
    }

    MergeExecutionSummary runMergeForFile(Path file) {
        LOG.trace("Starting execution of tool ${getToolName()} in ${file}")
        List<Long> executionTimes = new ArrayList<>()
        def outputFilePath = file.resolve("merge." + getToolName().toLowerCase() + this.extension)

        boolean toolTimedOut = false
        
        for (int i = 0; i < PERF_SAMPLING_TOTAL_NUMBER_OF_EXECUTIONS; i++) {
            LOG.trace("Starting execution ${i + 1} of ${PERF_SAMPLING_TOTAL_NUMBER_OF_EXECUTIONS}")
            long startTime = System.nanoTime()

            boolean success = executeTool(file, outputFilePath)

            long endTime = System.nanoTime()

            if (!success) {
                LOG.warn("Tool ${getToolName()} timed out during execution ${i + 1}. Aborting remaining runs for this file.")
                toolTimedOut = true
                break
            }

            LOG.trace("Finished execution ${i + 1} of ${PERF_SAMPLING_TOTAL_NUMBER_OF_EXECUTIONS} IN ${endTime - startTime} ns")
            // If we're running more than one execution, we use the first one as a warm up
            if (PERF_SAMPLING_TOTAL_NUMBER_OF_EXECUTIONS == 1 || i > 0) {
                executionTimes.add(endTime - startTime)
            }
        }

        MergeExecutionResult result
        long averageTime = 0L

        if (toolTimedOut) {
            result = MergeExecutionResult.TIMEOUT
        } else {
            result = decideResult(outputFilePath)

            if (!executionTimes.isEmpty()){
                averageTime = (long) (executionTimes.stream().reduce(0L, (prev, cur) -> prev + cur) / executionTimes.size())
            }
        }

        def summary = new MergeExecutionSummary(file, outputFilePath, result, averageTime)

        String toolName = getToolName().toLowerCase()
        File logFile = file.resolve("log_${toolName}.log").toFile()
        summary.extraMetrics = parseLogMetrics(logFile)

        LOG.trace("Finished execution of tool ${getToolName()} in ${file}. Execution took ${summary.time}ns and finished with ${summary.result.toString()} status")
        return summary
    }

    protected String getExecutionDirectory() {
        return System.getProperty("user.dir")
    }

    private boolean executeTool(Path file, Path outputFile) {
        def processBuilder = ProcessRunner.buildProcess(getExecutionDirectory())
        processBuilder.command().addAll(getArgumentsForTool(file, outputFile))

        LOG.trace("Calling tool ${getToolName()} with command \"${processBuilder.command().join(' ')}\"")
        
        String toolName = getToolName().toLowerCase()
        File logFile = file.resolve("log_${toolName}.log").toFile()
        processBuilder.redirectErrorStream(true)
        processBuilder.redirectOutput(logFile)
        
        def process = ProcessRunner.startProcess(processBuilder)
        boolean finishedInTime = process.waitFor(TIMEOUT_IN_MINUTES, TimeUnit.MINUTES)
        
        if (!finishedInTime) {
            process.destroyForcibly()
            LOG.warn("TIMEOUT EXCEEDED: Killed zombie process for ${getToolName()} to free RAM.")
            return false
        }

        return true
    }

    private static MergeExecutionResult decideResult(Path outputFile) {
        if (!Files.exists(outputFile)) {
            return MergeExecutionResult.TOOL_ERROR
        } else if (MergeConflict.getConflictsNumber(outputFile) > 0) {
            return MergeExecutionResult.SUCCESS_WITH_CONFLICTS
        }
        return MergeExecutionResult.SUCCESS_WITHOUT_CONFLICTS
    }

    protected Map<String, String> parseLogMetrics(File logFile) {
        return [:]
    }

    protected abstract List<String> getArgumentsForTool(Path file, Path outputFile);

    abstract String getToolName();
}