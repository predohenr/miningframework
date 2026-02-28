package services.commitFilters

import interfaces.CommitFilter
import project.MergeCommit
import project.Project

class CompositeCommitFilter implements CommitFilter {
    
    private final List<CommitFilter> filters

    CompositeCommitFilter(List<CommitFilter> filters) {
        this.filters = filters
    }

    @Override
    boolean applyFilter(Project project, MergeCommit mergeCommit) {
        return filters.every { it.applyFilter(project, mergeCommit) }
    }
}