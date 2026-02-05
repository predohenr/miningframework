package services.dataCollectors.common

import interfaces.DataCollector
import project.MergeCommit
import project.Project
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class RunDataCollectorsSequentially implements DataCollector {
    private static final Logger LOG = LogManager.getLogger(RunDataCollectorsSequentially.class)
    private final List<DataCollector> collectors

    RunDataCollectorsSequentially(List<DataCollector> collectors) {
        this.collectors = collectors
    }

    @Override
    void collectData(Project project, MergeCommit mergeCommit) {
        collectors.each { collector ->
            try {
                collector.collectData(project, mergeCommit)
            } catch (Exception e) {
                LOG.error("Error executing sequential collector.", e)
            }
        }
    }
}