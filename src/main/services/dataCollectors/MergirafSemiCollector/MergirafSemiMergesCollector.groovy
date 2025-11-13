package services.dataCollectors.MergirafSemiCollector

import java.nio.file.Path

import interfaces.DataCollector
import project.MergeCommit
import project.Project
import services.dataCollectors.MergirafSemiCollector.mergeToolRunners.*
import services.dataCollectors.S3MWithCSDiffCollector.mergeToolRunners.Diff3Runner
import services.dataCollectors.MergirafAndSepMergeCollector.mergeToolRunners.MergirafRunner
import services.dataCollectors.S3MMergesCollector.MergeScenarioCollector
import services.util.MergeToolRunner

class MergirafSemiMergesCollector implements DataCollector {
    private static final List<MergeToolRunner> RUNNERS = [
        new Diff3Runner(),
        new MergirafSemiRunnerC(),
        new MergirafSemiRunnerSC(),
        new MergirafRunner()
    ]

    @Override
    void collectData(Project project, MergeCommit mergeCommit) {
        List<Path> mergeScenarios = MergeScenarioCollector.collectMergeScenarios(project, mergeCommit)
        println 'Collected merge scenarios'

        for (MergeToolRunner runner: RUNNERS) {
            runner.collectResults(mergeScenarios)
        }

        println 'Collected merge results'

        List<MergirafSemiMergeSummary> summaries = MergirafSemiDataAnalyser.analyseMerges(mergeScenarios)
        println 'Built summaries over collected data'

        MergirafSemiSpreadsheetBuilder.updateSpreadsheet(project, mergeCommit, summaries)
        println 'Updated spreadsheet'
    }

    static List<String> getMergeToolNames() {
        return RUNNERS.stream().map(MergeToolRunner::getMergeToolName).toList()
    }
}
