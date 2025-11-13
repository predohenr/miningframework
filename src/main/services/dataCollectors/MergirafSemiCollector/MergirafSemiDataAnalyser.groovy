package services.dataCollectors.MergirafSemiCollector

import java.nio.file.Path

class MergirafSemiDataAnalyser {
    static List<MergirafSemiMergeSummary> analyseMerges(List<Path> filesQuadruplePaths) {
        return filesQuadruplePaths.stream().map(MergirafSemiMergeSummary::new).toList()
    }
}
