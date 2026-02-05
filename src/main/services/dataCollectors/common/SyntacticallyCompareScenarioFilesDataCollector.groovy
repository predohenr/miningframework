package services.dataCollectors.common

import interfaces.DataCollector
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import project.MergeCommit
import project.Project
import services.dataCollectors.S3MMergesCollector.MergeScenarioCollector
import util.CsvUtils

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors

class SyntacticallyCompareScenarioFilesDataCollector implements DataCollector {
    private static Logger LOG = LogManager.getLogger(SyntacticallyCompareScenarioFilesDataCollector.class)

    private static final REPORT_DIRECTORY = "${System.getProperty("user.dir")}/output/reports/syntactic-comparison"

    private String _fileA
    private String _fileB

    SyntacticallyCompareScenarioFilesDataCollector(String fileA, String fileB) {
        this._fileA = fileA
        this._fileB = fileB
    }

    @Override
    void collectData(Project project, MergeCommit mergeCommit) {
        def results = MergeScenarioCollector.collectNonFastForwardMergeScenarios(project, mergeCommit)
                .parallelStream()
                .map(file -> {
                    def fileA = file.resolve(_fileA)
                    def fileB = file.resolve(_fileB)
                    
                    //only compares textually
                    boolean areFilesSyntacticallyEquivalent = areFilesContentIdentical(fileA, fileB)
                    
                    return [project.getName(), mergeCommit.getSHA(), file, fileA, fileB, areFilesSyntacticallyEquivalent]
                })
                .map(CsvUtils::toCsvRepresentation)
                .collect(Collectors.toList())

        writeToReportFile(getReportFileName(), results)
    }

    protected synchronized static writeToReportFile(String reportFileName, List<String> lines) {
        def reportFile = new File(reportFileName)
        Files.createDirectories(Paths.get(REPORT_DIRECTORY))
        if(!reportFile.exists()) {
            reportFile.createNewFile()
        }
        reportFile << lines.stream().collect(CsvUtils.asLines()) << System.lineSeparator()
    }

    private String getReportFileName() {
        return "${REPORT_DIRECTORY}/${_fileA.replace('.', "_")}-${_fileB.replace('.', "_")}.csv"
    }

    private static boolean areFilesContentIdentical(Path pathA, Path pathB) {
        if (!Files.exists(pathA) || !Files.exists(pathB)) {
            LOG.trace("One of the files does not exist: ${pathA} or ${pathB}")
            return false
        }

        try {
            //optimization by comparing file sizes first
            if (Files.size(pathA) != Files.size(pathB)) {
                return false
            }

            byte[] bytesA = Files.readAllBytes(pathA)
            byte[] bytesB = Files.readAllBytes(pathB)
            return Arrays.equals(bytesA, bytesB)

        } catch (IOException e) {
            LOG.error("Error reading files for comparison: ${pathA} vs ${pathB}", e)
            return false
        }
    }
}