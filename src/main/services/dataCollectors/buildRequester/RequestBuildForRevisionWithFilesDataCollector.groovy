package services.dataCollectors.buildRequester

import interfaces.DataCollector
import org.apache.logging.log4j.LogManager
import project.MergeCommit
import project.Project
import services.dataCollectors.S3MMergesCollector.MergeScenarioCollector
import util.ProcessRunner

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.io.File

import static app.MiningFramework.arguments

class RequestBuildForRevisionWithFilesDataCollector implements DataCollector {
    private static LOG = LogManager.getLogger(RequestBuildForRevisionWithFilesDataCollector.class)

    private String fileName
    private String extension

    RequestBuildForRevisionWithFilesDataCollector(String fileName, String extension) {
        this.fileName = fileName
        this.extension = extension
    }

    @Override
    synchronized void collectData(Project project, MergeCommit mergeCommit) {
        if (!arguments.providedAnalysisRepo()) {
            LOG.warn("Skipping build analysis push: No analysis repository provided via --analysis-repo")
            return 
        }
    
        if (!arguments.providedAccessKey()) {
            LOG.warn("Skipping build analysis push: No access key provided, which is required for pushing to the analysis repo")
            return
        }

        def branchName = "mining-framework-analysis_${project.getName()}_${mergeCommit.getSHA()}_${fileName}"
        
        LOG.debug("Attaching origin to project")
        attachOrigin(project)
        
        LOG.debug("Setting up credentials")
        setupCredentials(project)
        
        LOG.debug("Deleting and creating branch")
        deleteBranch(project, branchName)
        
        LOG.debug("Checking out branch")
        checkoutCommitAndCreateBranch(project, branchName, mergeCommit.getSHA())
        
        LOG.debug("Copying files")
        copyFilesIntoRevision(project, mergeCommit)
        
        GithubActionsHelper.createGitHubActionsFile(project, this.extension)
        
        LOG.debug("Comitting files")
        commitChanges(project, "Mining Framework Analysis for ${fileName}")
        
        LOG.debug("Pushing analysis")
        pushBranch(project, branchName)
    }

    private synchronized copyFilesIntoRevision(Project project, MergeCommit mergeCommit) {
        def scenarioFiles = MergeScenarioCollector.collectNonFastForwardMergeScenarios(project, mergeCommit)
        
        scenarioFiles.stream().sequential()
                .filter(file -> {
                    if (Files.notExists(file.resolve(this.fileName))) {
                        LOG.debug("Skipping copy of file ${file.resolve(this.fileName).toAbsolutePath().toString()} because it does not exist in scenario")
                        return false
                    }
                    return true
                })
                .forEach(file -> {
                    try {
                        String relativePath = file.toString().substring(file.toString().indexOf(mergeCommit.getSHA()) + 1 + mergeCommit.getSHA().length())

                        if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
                            relativePath = relativePath.substring(1)
                        }

                        def destination = Paths.get(project.getPath()).resolve(relativePath)
                        
                        File destFile = destination.toFile()
                        if (destFile.getParentFile() != null && !destFile.getParentFile().exists()) {
                            LOG.info("Creating missing directory: " + destFile.getParentFile().getAbsolutePath())
                            destFile.getParentFile().mkdirs()
                        }

                        LOG.debug("Copying file ${file.resolve(this.fileName)} to ${destination}")
                        
                        Files.copy(file.resolve(this.fileName), destination, StandardCopyOption.REPLACE_EXISTING)
                        
                    } catch (Exception e) {
                        LOG.error("Failed to copy file ${this.fileName}: ${e.getMessage()}")
                        e.printStackTrace()
                    }
                })
    }

    static private void attachOrigin(Project project) {
        def token = arguments.getAccessKey()
        def repoPath = arguments.getAnalysisRepo()
        def origin = "https://${token}@github.com/${repoPath}"
        def process = ProcessRunner.runProcess(project.getPath(), 'git', 'remote', 'add', 'analysis', origin)
        process.getInputStream().eachLine(LOG::trace)
        process.getErrorStream().eachLine(LOG::warn)
        process.waitFor()
    }

    static private void setupCredentials(Project project) {
        def configEmail = ProcessRunner.runProcess(project.getPath(), 'git', 'config', 'user.email', '"predohnr@gmail.com"')
        configEmail.getInputStream().eachLine(LOG::trace)
        configEmail.getErrorStream().eachLine(LOG::warn)
        configEmail.waitFor()
        def configName = ProcessRunner.runProcess(project.getPath(), 'git', 'config', 'user.name', '"Pedro Henrique"')
        configName.getInputStream().eachLine(LOG::trace)
        configName.getErrorStream().eachLine(LOG::warn)
        configName.waitFor()
    }

    static private void deleteBranch(Project project, String branchName) {
        def process = ProcessRunner.runProcess(project.getPath(), 'git', 'branch', '-D', branchName)
        process.getInputStream().eachLine(LOG::trace)
        process.getErrorStream().eachLine(LOG::warn)
        process.waitFor()
    }

    static private void checkoutCommitAndCreateBranch(Project project, String branchName, String commitSha) {
        ProcessRunner.runProcess(project.getPath(), 'git', 'checkout', '-f', commitSha).waitFor()
        def process = ProcessRunner.runProcess(project.getPath(), 'git', 'checkout', '-b', branchName)
        process.getInputStream().eachLine(LOG::trace)
        process.getErrorStream().eachLine(LOG::warn)
        process.waitFor()
    }

    static private void pushBranch(Project project, String branchName) {
        def process = ProcessRunner.runProcess(project.getPath(), 'git', 'push', 'analysis', branchName, "-f")
        process.getInputStream().eachLine(LOG::trace)
        process.getErrorStream().eachLine(LOG::warn)
        process.waitFor()
    }

    static protected void commitChanges(Project project, String message) {
        def forceAddAction = ProcessRunner.runProcess(project.getPath(), "git", "add", "-f", ".github/workflows/")
        forceAddAction.getInputStream().eachLine(LOG::trace)
        forceAddAction.getErrorStream().eachLine(LOG::warn)
        forceAddAction.waitFor()

        def process = ProcessRunner.runProcess(project.getPath(), "git", "add", ".")
        process.getInputStream().eachLine(LOG::trace)
        process.getErrorStream().eachLine(LOG::warn)
        process.waitFor()
        
        def commit = ProcessRunner.runProcess(project.getPath(), "git", "commit", "-m", "${message}")
        commit.getInputStream().eachLine(LOG::trace)
        commit.getErrorStream().eachLine(LOG::warn)
        commit.waitFor()
    }
}