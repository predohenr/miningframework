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
        def origin = "https://${token}@github.com/predohenr/mining-framework-analysis"
        def process = ProcessRunner.runProcess(project.getPath(), 'git', 'remote', 'add', 'analysis', origin)
        process.waitFor()
    }

    static private void setupCredentials(Project project) {
        ProcessRunner.runProcess(project.getPath(), 'git', 'config', 'user.email', '"predohnr@gmail.com"').waitFor()
        ProcessRunner.runProcess(project.getPath(), 'git', 'config', 'user.name', '"Pedro Henrique"').waitFor()
    }

    static private void deleteBranch(Project project, String branchName) {
        ProcessRunner.runProcess(project.getPath(), 'git', 'branch', '-D', branchName).waitFor()
    }

    static private void checkoutCommitAndCreateBranch(Project project, String branchName, String commitSha) {
        ProcessRunner.runProcess(project.getPath(), 'git', 'checkout', '-f', commitSha).waitFor()
        ProcessRunner.runProcess(project.getPath(), 'git', 'checkout', '-b', branchName).waitFor()
    }

    static private void pushBranch(Project project, String branchName) {
        ProcessRunner.runProcess(project.getPath(), 'git', 'push', 'analysis', branchName, "-f").waitFor()
    }

    static protected void commitChanges(Project project, String message) {
        ProcessRunner.runProcess(project.getPath(), "git", "add", "-f", ".github/workflows/").waitFor()
        ProcessRunner.runProcess(project.getPath(), "git", "add", "-A").waitFor() // Atualizado para -A por garantia
        ProcessRunner.runProcess(project.getPath(), "git", "commit", "-m", "${message}").waitFor()
    }
}