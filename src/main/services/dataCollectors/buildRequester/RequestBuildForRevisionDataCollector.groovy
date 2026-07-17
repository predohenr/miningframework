package services.dataCollectors.buildRequester

import org.apache.logging.log4j.LogManager
import project.Project
import util.ProcessRunner

import static app.MiningFramework.arguments

class RequestBuildForRevisionDataCollector {
    private static LOG = LogManager.getLogger(RequestBuildForRevisionDataCollector.class)

    private String extension

    RequestBuildForRevisionDataCollector(String extension) {
        this.extension = extension
    }

    synchronized void collectData(Project project, String sha) {
        if (!arguments.providedAnalysisRepo()) {
            LOG.warn("Skipping build analysis push: No analysis repository provided via --analysis-repo")
            return 
        }
    
        if (!arguments.providedAccessKey()) {
            LOG.warn("Skipping build analysis push: No access key provided, which is required for pushing to the analysis repo")
            return
        }

        def branchName = "mining-framework-analysis_${project.getName()}_${sha}_parent_build"
        
        LOG.debug("Attaching origin to project")
        attachOrigin(project)
        
        LOG.debug("Setting up credentials")
        setupCredentials(project)
        
        LOG.debug("Deleting and creating branch")
        deleteBranch(project, branchName)
        
        LOG.debug("Checking out branch")
        checkoutCommitAndCreateBranch(project, branchName, sha)
        
        GithubActionsHelper.createGitHubActionsFile(project, this.extension)
        
        LOG.debug("Comitting files")
        commitChanges(project, "Mining Framework Analysis for Parent Commit ${sha}")
        
        LOG.debug("Pushing analysis")
        pushBranch(project, branchName)
    }

    static private void attachOrigin(Project project) {
        def token = arguments.getAccessKey()
        def repoPath = arguments.getAnalysisRepo()       
        def origin = "https://${token.trim()}@github.com/${repoPath.trim()}"
        def process = ProcessRunner.runProcess(project.getPath(), 'git', 'remote', 'add', 'analysis', origin)
        process.getInputStream().eachLine(LOG::trace)
        process.getErrorStream().eachLine(LOG::warn)
        process.waitFor()
    }

    static private void setupCredentials(Project project) {
        def configEmail = ProcessRunner.runProcess(project.getPath(), 'git', 'config', 'user.email', '"miningworker@gmail.com"')
        configEmail.getInputStream().eachLine(LOG::trace)
        configEmail.getErrorStream().eachLine(LOG::warn)
        configEmail.waitFor()
        
        def configName = ProcessRunner.runProcess(project.getPath(), 'git', 'config', 'user.name', '"Mining Worker"')
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

        def process = ProcessRunner.runProcess(project.getPath(), "git", "add", "-A")
        process.getInputStream().eachLine(LOG::trace)
        process.getErrorStream().eachLine(LOG::warn)
        process.waitFor()
        
        def commit = ProcessRunner.runProcess(project.getPath(), "git", "commit", "-m", "${message}")
        commit.getInputStream().eachLine(LOG::trace)
        commit.getErrorStream().eachLine(LOG::warn)
        commit.waitFor()
    }
}