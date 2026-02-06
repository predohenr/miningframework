package services.dataCollectors.common

import interfaces.DataCollector
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.commons.io.FileUtils
import project.MergeCommit
import project.Project
import services.dataCollectors.S3MMergesCollector.MergeScenarioCollector
import services.dataCollectors.buildRequester.RequestBuildForRevisionWithFilesDataCollector
import services.util.MergeConflict
import java.nio.file.Files
import java.nio.file.Path

class ConsensusBuildDataCollector implements DataCollector {
    private static Logger LOG = LogManager.getLogger(ConsensusBuildDataCollector.class)

    private final String fileExtension
    private final String cleanExtension 
    
    private final List<String> tools = ["mergiraf", "mergiraf_semi_c", "mergiraf_semi_sc", "diff3"]

    ConsensusBuildDataCollector(String fileExtension, String cleanExtension) {
        this.fileExtension = fileExtension
        this.cleanExtension = cleanExtension
    }

    @Override
    void collectData(Project project, MergeCommit mergeCommit) {
        def scenarios = MergeScenarioCollector.collectNonFastForwardMergeScenarios(project, mergeCommit)

        scenarios.parallelStream().forEach({ Path scenario ->
            try {
                analyzeAndTriggerBuilds(project, mergeCommit, scenario)
            } catch (Exception e) {
                LOG.error("Consensus error in scenario ${scenario}: ${e.message}", e)
            }
        })
    }

    private void analyzeAndTriggerBuilds(Project project, MergeCommit mergeCommit, Path scenarioDir) {
        Map<String, Boolean> conflictsMap = [:]
        Map<String, Path> toolPaths = [:]
        String fileName = scenarioDir.getFileName().toString()

        for (String tool : tools) {
            String toolFileName = "merge.${tool}${this.fileExtension}" 
            Path toolOutput = scenarioDir.resolve(toolFileName)

            if (Files.exists(toolOutput)) {
                toolPaths[tool] = toolOutput
                conflictsMap[tool] = MergeConflict.getConflictsNumber(toolOutput) > 0
            } else {
                conflictsMap[tool] = true
            }
        }

        boolean allConflict = conflictsMap.values().stream().allMatch({ it == true })
        if (allConflict) {
            LOG.info("SKIP [ALL-CONFLICT]: All failed in '${fileName}'")
            return
        }

        boolean noneConflict = conflictsMap.values().stream().allMatch({ it == false })
        if (noneConflict) {
            LOG.info("SKIP [ALL-CLEAN]: All solved in '${fileName}'")
            return
        }

        Path groundTruth = scenarioDir.resolve("merge${this.fileExtension}") 
        boolean hasGroundTruth = Files.exists(groundTruth)

        toolPaths.each { tool, path ->
            boolean isClean = !conflictsMap[tool]
            
            if (isClean) {
                boolean differsFromHuman = true
                
                if (hasGroundTruth) {
                    if (FileUtils.contentEquals(path.toFile(), groundTruth.toFile())) {
                        differsFromHuman = false
                    }
                }

                if (differsFromHuman) {
                    LOG.info("BUILD TRIGGER: '${tool}' in '${fileName}'")
                    
                    new RequestBuildForRevisionWithFilesDataCollector(
                        "merge.${tool}${this.fileExtension}", 
                        this.cleanExtension
                    ).collectData(project, mergeCommit)

                } else {
                    LOG.info("SKIP [MATCH-GT]: '${tool}' in '${fileName}'")
                }
            }
        }
    }
}