package services.dataCollectors.fileSyntacticNormalization

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.stream.Collectors 

class GenericTextNormalizerDataCollector extends BaseFileSyntacticNormalizationDataCollector {
    private static Logger LOG = LogManager.getLogger(GenericTextNormalizerDataCollector.class)
    
    private final String targetExtension

    GenericTextNormalizerDataCollector(String inputFile, String outputFile, String targetExtension) {
        super(inputFile, outputFile)
        this.targetExtension = targetExtension.replace(".", "").toLowerCase()
    }

    @Override
    protected boolean runNormalizationOnFile(Path inputFile, Path outputFile) {
        try {
            String content = Files.readString(inputFile)
            String normalizedContent

            // regex strategy
            normalizedContent = ScriptBasedNormalizer.normalize(content, this.targetExtension)

            // script strategy
            /*
            if (["py", "js", "ts", "go", "rs", "java"].contains(this.targetExtension)) {
                normalizedContent = ScriptBasedNormalizer.normalize(content, this.targetExtension)

            }
            */

            Files.writeString(outputFile, normalizedContent,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)

            return true

        } catch (Exception e) {
            LOG.error("Failed to normalize file ${inputFile}: ${e.message}")
            return false
        }
    }
}