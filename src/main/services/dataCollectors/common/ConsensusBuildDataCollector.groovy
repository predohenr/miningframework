package services.dataCollectors.common

import interfaces.DataCollector
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import project.MergeCommit
import project.Project
import services.dataCollectors.S3MMergesCollector.MergeScenarioCollector
import services.dataCollectors.buildRequester.RequestBuildForRevisionWithFilesDataCollector
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

class ConsensusBuildDataCollector implements DataCollector {
    private static Logger LOG = LogManager.getLogger(ConsensusBuildDataCollector.class)

    private final String fileExtension
    private final String cleanExtension 
    
    private final List<String> tools = ["mergiraf", "mergiraf_semi", "mergiraf_semi_plus", "diff3"]

    ConsensusBuildDataCollector(String fileExtension, String cleanExtension) {
        this.fileExtension = fileExtension
        this.cleanExtension = cleanExtension
    }

    @Override
    void collectData(Project project, MergeCommit mergeCommit) {
        def scenarios = MergeScenarioCollector.collectNonFastForwardMergeScenarios(project, mergeCommit)

        if (scenarios == null || scenarios.isEmpty()) {
            return
        }

        for (String tool : tools) {
            boolean isCleanGlobally = true
            boolean differsFromHumanGlobally = false
            
            for (Path scenarioDir : scenarios) {
                String toolFileName = "merge.${tool}${this.fileExtension}" 
                Path toolOutput = scenarioDir.resolve(toolFileName)
                Path groundTruth = scenarioDir.resolve("merge${this.fileExtension}") 

                if (!Files.exists(toolOutput)) {
                    isCleanGlobally = false
                    break 
                }

                if (hasSurvivingMarkers(toolOutput)) {
                    LOG.warn("SKIP [CONFLICTING-FILE]: Tool '${tool}' FAILED in file ${scenarioDir.getFileName()}.")
                    isCleanGlobally = false
                    break
                }

                if (Files.exists(groundTruth)) {
                    if (!FileUtils.contentEquals(toolOutput.toFile(), groundTruth.toFile())) {
                        differsFromHumanGlobally = true
                    }
                } else {
                    differsFromHumanGlobally = true 
                }
            }

            if (isCleanGlobally) {
                if (differsFromHumanGlobally) {
                    LOG.info("BUILD TRIGGER: '${tool}' passed in commit ${mergeCommit.getSHA()}.")
                    
                    new RequestBuildForRevisionWithFilesDataCollector(
                        "merge.${tool}${this.fileExtension}", 
                        this.cleanExtension
                    ).collectData(project, mergeCommit)

                } else {
                    LOG.info("SKIP [MATCH-GT]: Tool '${tool}' in commit ${mergeCommit.getSHA()}.")
                }
            }
        }
    }

    private boolean hasSurvivingMarkers(Path file) {
        try {
            Iterator<String> lines = FileUtils.readLines(file.toFile(), Charset.defaultCharset()).iterator()
            while (lines.hasNext()) {
                String cleanLine = StringUtils.deleteWhitespace(lines.next())
                
                if (cleanLine.startsWith("<<<<<<<") || 
                    cleanLine.startsWith("=======") || 
                    cleanLine.startsWith("|||||||") || 
                    cleanLine.startsWith(">>>>>>>")) {
                    return true 
                }
            }
        } catch (Exception e) {
            LOG.error("Error reading file while looking for conflict markers: ${e.message}")
            return true 
        }
        return false 
    }
}