package services.dataCollectors.GenericMerge

import app.MiningFramework
import interfaces.DataCollector
import project.MergeCommit
import project.Project
import util.ProcessRunner
import java.io.File

class SampleAnotatorDataCollector implements DataCollector {
    private File outputFile = null

    private synchronized void initFileIfNeeded() {
        if (outputFile == null) {
            def args = MiningFramework.arguments
            String rawExtension = args?.getFileExtension() ?: "results"
            String cleanExt = rawExtension.replace(".", "")
            
            String outputDir = args?.getOutputPath() ?: "." 
            
            String fileName = "sample_${cleanExt}.csv"
            
            this.outputFile = new File(outputDir, fileName)
            
            if (this.outputFile.parentFile != null) {
                this.outputFile.parentFile.mkdirs()
            }
            
            if (!this.outputFile.exists() || this.outputFile.length() == 0) {
                this.outputFile.write("project,total_project_merges,merge_sha,parent1_sha,parent2_sha\n")
            }
        }
    }

    @Override
    synchronized void collectData(Project project, MergeCommit mergeCommit) {
        initFileIfNeeded()

        String mergeSha = mergeCommit.getSHA()

        def parentProcess = ProcessRunner.runProcess(project.getPath(), "git", "log", "-1", "--pretty=%P", mergeSha)
        parentProcess.waitFor()
        String parentsLine = parentProcess.getInputStream().text.trim()
        String[] parents = parentsLine.split(" ")
        String parent1 = parents.length > 0 ? parents[0] : ""
        String parent2 = parents.length > 1 ? parents[1] : ""

        def countProcess = ProcessRunner.runProcess(project.getPath(), "git", "rev-list", "--merges", "--count", "HEAD")
        countProcess.waitFor()
        String totalMerges = countProcess.getInputStream().text.trim()

        String csvLine = "${project.getName()},${totalMerges},${mergeSha},${parent1},${parent2}\n"
        outputFile.append(csvLine)
        
        println "Coletado: ${project.getName()} -> ${mergeSha} (Total de merges no repo: ${totalMerges})"
    }
}