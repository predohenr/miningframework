package services.dataCollectors.common

import interfaces.DataCollector
import project.MergeCommit
import project.Project
import services.dataCollectors.S3MMergesCollector.MergeScenarioCollector
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.io.File

class ConditionalBuildDataCollector implements DataCollector {
    private static final Logger LOG = LogManager.getLogger(ConditionalBuildDataCollector.class)

    // Nomes dos arquivos (ex: merge.mergiraf.java e merge.java)
    private final String normalizedToolFile
    private final String normalizedGroundTruthFile
    private final DataCollector buildCollector

    // CONSTRUTOR COM 3 ARGUMENTOS (Compatível com GenericMergeModule)
    ConditionalBuildDataCollector(String normalizedToolFile, String normalizedGroundTruthFile, DataCollector buildCollector) {
        this.normalizedToolFile = normalizedToolFile
        this.normalizedGroundTruthFile = normalizedGroundTruthFile
        this.buildCollector = buildCollector
    }

    @Override
    void collectData(Project project, MergeCommit mergeCommit) {
        List<Path> scenarioPaths = MergeScenarioCollector.collectNonFastForwardMergeScenarios(project, mergeCommit)

        if (scenarioPaths.isEmpty()) {
            return
        }

        boolean shouldBuild = false
        int filesChecked = 0

        for (Path scenarioPath : scenarioPaths) {
            File toolFile = scenarioPath.resolve(normalizedToolFile).toFile()
            File groundTruthFile = scenarioPath.resolve(normalizedGroundTruthFile).toFile()

            if (!toolFile.exists()) {
                continue
            }
            filesChecked++

            if (!groundTruthFile.exists()) {
                LOG.warn("Ground Truth faltando em ${scenarioPath.getFileName()}. Marcando para build.")
                shouldBuild = false
                break
            }

            try {
                String contentTool = Files.readString(toolFile.toPath())
                String contentTruth = Files.readString(groundTruthFile.toPath())

                if (!contentTool.equals(contentTruth)) {
                    LOG.info("Diferença encontrada no arquivo do cenário: ${scenarioPath.getFileName()}")
                    shouldBuild = true
                    break
                }

            } catch (Exception e) {
                LOG.error("Erro ao ler arquivos. Forçando build.", e)
                shouldBuild = true
                break
            }
        }

        if (filesChecked == 0) {
            LOG.info("Nenhum arquivo '${normalizedToolFile}' encontrado nos cenários deste commit.")
        } else if (shouldBuild) {
            LOG.info("REQUESTING BUILD: Pelo menos um arquivo difere do Ground Truth. Disparando build...")
            buildCollector.collectData(project, mergeCommit)
        } else {
            LOG.info("SKIPPING BUILD: Todos os ${filesChecked} arquivos verificados são IDÊNTICOS ao Ground Truth.")
        }
    }
}