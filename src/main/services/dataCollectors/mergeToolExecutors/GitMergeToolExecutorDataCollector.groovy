package services.dataCollectors.mergeToolExecutors

import interfaces.DataCollector
import project.MergeCommit
import project.Project
import services.dataCollectors.S3MMergesCollector.MergeScenarioCollector
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class GitMergeToolExecutorDataCollector implements DataCollector {

    private String extension

    GitMergeToolExecutorDataCollector(String extension) {
        this.extension = extension
    }

    @Override
    void collectData(Project project, MergeCommit mergeCommit) {
        List<Path> scenarios = MergeScenarioCollector.collectNonFastForwardMergeScenarios(project, mergeCommit)

        scenarios.parallelStream().forEach({ Path scenario ->
            try {
                Path basePath = scenario.resolve("base" + extension)
                Path leftPath = scenario.resolve("left" + extension)
                Path rightPath = scenario.resolve("right" + extension)
                Path outputPath = scenario.resolve("merge.diff3" + extension)

                // merge is a copy of left for git merge-file to overwrite it
                Files.copy(leftPath, outputPath, StandardCopyOption.REPLACE_EXISTING)

                ProcessBuilder pb = new ProcessBuilder("git", "merge-file", 
                    outputPath.toString(), 
                    basePath.toString(), 
                    rightPath.toString()
                )
                
                pb.directory(scenario.toFile())
                Process p = pb.start()
                p.waitFor()

            } catch (Exception e) {
                e.printStackTrace()
            }
        })
    }
}