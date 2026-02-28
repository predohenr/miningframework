package services.commitFilters

import interfaces.CommitFilter
import project.MergeCommit
import project.Project
import util.ProcessRunner

class OldestBuildableCommitFilter implements CommitFilter {

    @Override
    boolean applyFilter(Project project, MergeCommit mergeCommit) {
        String oldestBuildableSHA = project.getFirstBuildableSHA()

        if (oldestBuildableSHA == null || oldestBuildableSHA.trim().isEmpty()) {
            return true
        }

        List<String> parameters = ['git', 'merge-base', '--is-ancestor', oldestBuildableSHA, mergeCommit.getSHA()]

        ProcessBuilder processBuilder = ProcessRunner.buildProcess(project.getPath())
        processBuilder.command().addAll(parameters)

        try {
            Process process = ProcessRunner.startProcess(processBuilder)
            int exitCode = process.waitFor()
            
            return exitCode == 0
        } catch (Exception e) {
            return false //if error discard commit
        }
    }
}