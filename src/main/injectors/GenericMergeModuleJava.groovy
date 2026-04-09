package injectors

import app.MiningFramework
import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import interfaces.CommitFilter
import interfaces.DataCollector
import interfaces.OutputProcessor
import interfaces.ProjectProcessor
import project.Project
import project.MergeCommit
import services.commitFilters.MutuallyModifiedFilesCommitFilter
import services.commitFilters.OldestBuildableCommitFilter
import services.commitFilters.CompositeCommitFilter
import services.dataCollectors.common.CompareScenarioMergeConflictsDataCollector
import services.dataCollectors.common.ConsensusBuildDataCollector
import services.dataCollectors.common.ConsensusBuildDataCollectorJava
import services.dataCollectors.common.RunDataCollectorsInParallel
import services.dataCollectors.common.RunDataCollectorsSequentially
import services.dataCollectors.common.SyntacticallyCompareScenarioFilesDataCollector
import services.dataCollectors.fileSyntacticNormalization.GenericTextNormalizerDataCollector
import services.dataCollectors.mergeToolExecutors.*
import services.outputProcessors.EmptyOutputProcessor
import services.projectProcessors.DummyProjectProcessor

class GenericMergeModuleJava extends AbstractModule {

    GenericMergeModuleJava() {
    }

    @Override
    protected void configure() {
        Multibinder<ProjectProcessor> projectProcessorBinder = Multibinder.newSetBinder(binder(), ProjectProcessor.class)
        projectProcessorBinder.addBinding().to(DummyProjectProcessor.class)

        Multibinder<DataCollector> dataCollectorBinder = Multibinder.newSetBinder(binder(), DataCollector.class)

        dataCollectorBinder.addBinding().toInstance(new LazyCollector({
            def exts = getExtensions()
            
            return new RunDataCollectorsSequentially([
                new MergirafMergeToolExecutorDataCollector(exts.file),
                new MergirafSemiCMergeToolExecutorDataCollector(exts.file),
                new MergirafSemiSCMergeToolExecutorDataCollector(exts.file),
                new S3MMergeToolExecutorDataCollector(exts.file), //s3m
                new GitMergeToolExecutorDataCollector(exts.file)
            ])
        }))

        dataCollectorBinder.addBinding().toInstance(new LazyCollector({
            def exts = getExtensions()
            return new RunDataCollectorsSequentially([
                new GenericTextNormalizerDataCollector("merge.mergiraf${exts.file}", "merge.mergiraf.format_normalized${exts.file}", exts.clean),
                new GenericTextNormalizerDataCollector("merge.mergiraf_semi${exts.file}", "merge.mergiraf_semi.format_normalized${exts.file}", exts.clean),
                new GenericTextNormalizerDataCollector("merge.mergiraf_semi_plus${exts.file}", "merge.mergiraf_semi_plus.format_normalized${exts.file}", exts.clean),
                new GenericTextNormalizerDataCollector("merge.diff3${exts.file}", "merge.diff3.format_normalized${exts.file}", exts.clean),
                new GenericTextNormalizerDataCollector("merge.s3m${exts.file}", "merge.s3m.format_normalized${exts.file}", exts.clean),
                new GenericTextNormalizerDataCollector("merge${exts.file}", "merge.format_normalized${exts.file}", exts.clean)
            ])
        }))

        dataCollectorBinder.addBinding().toInstance(new LazyCollector({
            def exts = getExtensions()
            return new RunDataCollectorsSequentially([
                // semi vs semi+
                new SyntacticallyCompareScenarioFilesDataCollector("merge.mergiraf_semi_plus.format_normalized${exts.file}", "merge.mergiraf_semi.format_normalized${exts.file}"),
                
                // semi vs structured
                new SyntacticallyCompareScenarioFilesDataCollector("merge.mergiraf_semi.format_normalized${exts.file}", "merge.mergiraf.format_normalized${exts.file}"),
                new SyntacticallyCompareScenarioFilesDataCollector("merge.mergiraf_semi_plus.format_normalized${exts.file}", "merge.mergiraf.format_normalized${exts.file}"),
                
                // semi vs diff3
                new SyntacticallyCompareScenarioFilesDataCollector("merge.mergiraf_semi.format_normalized${exts.file}", "merge.diff3.format_normalized${exts.file}"),
                new SyntacticallyCompareScenarioFilesDataCollector("merge.mergiraf_semi_plus.format_normalized${exts.file}", "merge.diff3.format_normalized${exts.file}"),
                new SyntacticallyCompareScenarioFilesDataCollector("merge.s3m.format_normalized${exts.file}", "merge.diff3.format_normalized${exts.file}"),

                //semi generic vs semi specific
                new SyntacticallyCompareScenarioFilesDataCollector("merge.mergiraf_semi.format_normalized${exts.file}", "merge.s3m.format_normalized${exts.file}"),
                new SyntacticallyCompareScenarioFilesDataCollector("merge.mergiraf_semi_plus.format_normalized${exts.file}", "merge.s3m.format_normalized${exts.file}"),

                // tools vs repo merge
                new SyntacticallyCompareScenarioFilesDataCollector("merge.mergiraf.format_normalized${exts.file}", "merge.format_normalized${exts.file}"),
                new SyntacticallyCompareScenarioFilesDataCollector("merge.mergiraf_semi.format_normalized${exts.file}", "merge.format_normalized${exts.file}"),
                new SyntacticallyCompareScenarioFilesDataCollector("merge.mergiraf_semi_plus.format_normalized${exts.file}", "merge.format_normalized${exts.file}"),
                new SyntacticallyCompareScenarioFilesDataCollector("merge.s3m.format_normalized${exts.file}", "merge.format_normalized${exts.file}"),
                new SyntacticallyCompareScenarioFilesDataCollector("merge.diff3.format_normalized${exts.file}", "merge.format_normalized${exts.file}"),
            ])
        }))

        // build
        dataCollectorBinder.addBinding().toInstance(new LazyCollector({
            def exts = getExtensions()
            return new ConsensusBuildDataCollectorJava(exts.file, exts.clean)
        }))

        Multibinder<OutputProcessor> outputProcessorBinder = Multibinder.newSetBinder(binder(), OutputProcessor.class)
        outputProcessorBinder.addBinding().to(EmptyOutputProcessor.class)

        bind(CommitFilter.class).toInstance(new CompositeCommitFilter([
            new OldestBuildableCommitFilter(),
            new MutuallyModifiedFilesCommitFilter()
        ]))
    }

    private static Map<String, String> getExtensions() {
        def args = MiningFramework.arguments
        String rawExtension = args.getFileExtension() ?: "java"
        String fileExt = rawExtension.startsWith(".") ? rawExtension : "." + rawExtension
        String cleanExt = rawExtension.replace(".", "")
        return [file: fileExt, clean: cleanExt]
    }

    static class LazyCollector implements DataCollector {
        private final Closure<DataCollector> factory
        private DataCollector delegate

        LazyCollector(Closure<DataCollector> factory) {
            this.factory = factory
        }

        @Override
        void collectData(Project project, MergeCommit mergeCommit) {
            if (delegate == null) {
                delegate = factory.call() 
            }
            delegate.collectData(project, mergeCommit)
        }
    }
}