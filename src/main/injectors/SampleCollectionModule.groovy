package injectors

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import interfaces.CommitFilter
import interfaces.DataCollector
import interfaces.OutputProcessor
import interfaces.ProjectProcessor
import services.commitFilters.MutuallyModifiedFilesCommitFilter
import services.commitFilters.OldestBuildableCommitFilter
import services.commitFilters.CompositeCommitFilter
import services.outputProcessors.EmptyOutputProcessor
import services.projectProcessors.DummyProjectProcessor
import services.dataCollectors.GenericMerge.SampleAnotatorDataCollector

class SampleCollectionModule extends AbstractModule {

    SampleCollectionModule() {}

    @Override
    protected void configure() {
        Multibinder<ProjectProcessor> projectProcessorBinder = Multibinder.newSetBinder(binder(), ProjectProcessor.class)
        projectProcessorBinder.addBinding().to(DummyProjectProcessor.class)

        Multibinder<DataCollector> dataCollectorBinder = Multibinder.newSetBinder(binder(), DataCollector.class)
        dataCollectorBinder.addBinding().to(SampleAnotatorDataCollector.class)

        Multibinder<OutputProcessor> outputProcessorBinder = Multibinder.newSetBinder(binder(), OutputProcessor.class)
        outputProcessorBinder.addBinding().to(EmptyOutputProcessor.class)

        bind(CommitFilter.class).toInstance(new CompositeCommitFilter([
            new OldestBuildableCommitFilter(),
            new MutuallyModifiedFilesCommitFilter()
        ]))
    }
}