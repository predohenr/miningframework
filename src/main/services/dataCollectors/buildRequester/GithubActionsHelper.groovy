package services.dataCollectors.buildRequester

import project.Project
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.regex.Matcher
import java.util.regex.Pattern

class GithubActionsHelper {

    static void createGitHubActionsFile(Project project, String extension) {
        Path projectPath = Paths.get(project.getPath())
        String cleanExt = extension.replace(".", "").toLowerCase()
        String buildSteps = detectBuildSteps(projectPath, cleanExt)

        def contents = """
name: Mining Framework Check
on: [push]
jobs:
    test:
        runs-on: ubuntu-latest
        steps:
            - uses: actions/checkout@v4
${buildSteps}
        """

        def githubActionsDirectory = projectPath.resolve(".github/workflows")
        if (!Files.exists(githubActionsDirectory)) {
            Files.createDirectories(githubActionsDirectory)
        }
        
        Files.write(githubActionsDirectory.resolve("mining_framework.yaml"), contents.getBytes(Charset.defaultCharset()),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)
    }

    private static String detectBuildSteps(Path root, String ext) {    
        if (ext == "py") return getPythonSteps()
        
        if (ext == "java") {
            if (Files.exists(root.resolve("pom.xml"))) {
                return getMavenSteps(root)
            }
            if (Files.exists(root.resolve("build.gradle")) || Files.exists(root.resolve("build.gradle.kts"))) {
                return getGradleSteps(root)
            }
            return """
            - name: Set up JDK 17
              uses: actions/setup-java@v4
              with:
                java-version: '17'
                distribution: 'temurin'
            - name: Compile Java File
              run: find . -name "*.java" | xargs javac
            """
        }

        if (ext == "js" || ext == "ts" || ext == "jsx" || ext == "tsx") {
            if (Files.exists(root.resolve("package.json"))) {
                boolean usesYarn = Files.exists(root.resolve("yarn.lock"))
                return getNodeSteps(usesYarn)
            }
            return """
            - name: Set up Node.js
              uses: actions/setup-node@v4
              with:
                node-version: 20
            - name: Run Node Check
              run: node --check ./**/*.js
            """
        }
        
        if (ext == "go") return getGoSteps()
        if (ext == "rs") return getRustSteps()

        return """
            - name: Unknown Project Type
              run: echo "No build strategy found for extension .${ext}"
        """
    }

    private static String getPythonSteps() {
        return """
            - name: Set up Python
              uses: actions/setup-python@v4
              with:
                python-version: '3.10'
                cache: 'pip'
            - name: Install dependencies
              run: |
                python -m pip install --upgrade pip
                pip install pytest
                if [ -f requirements.txt ]; then pip install -r requirements.txt; fi
                if [ -f pyproject.toml ]; then pip install .; fi
            - name: Run Tests
              uses: nick-fields/retry@v3
              with:
                timeout_minutes: 20
                max_attempts: 3
                command: pytest || python -m unittest discover
        """
    }

    private static String getMavenSteps(Path root) {
        String javaVersion = detectJavaVersionForMaven(root)
        
        return """
            - name: Set up JDK ${javaVersion}
              uses: actions/setup-java@v4
              with:
                java-version: '${javaVersion}'
                distribution: 'temurin'
                cache: maven
            - name: Run Maven Tests
              uses: nick-fields/retry@v3
              with:
                timeout_minutes: 30
                max_attempts: 3
                command: mvn -B test
        """
    }

    private static String detectJavaVersionForMaven(Path root) {
        try {
            Path pomPath = root.resolve("pom.xml")
            if (Files.exists(pomPath)) {
                String content = new String(Files.readAllBytes(pomPath))
                
                // Tenta encontrar a versão nas diferentes tags comuns do Maven
                Matcher javaVersionMatcher = Pattern.compile("<java\\.version>(.+?)</java\\.version>").matcher(content)
                if (javaVersionMatcher.find()) {
                    return normalizeJavaVersion(javaVersionMatcher.group(1).trim())
                }
                
                Matcher compilerTargetMatcher = Pattern.compile("<maven\\.compiler\\.target>(.+?)</maven\\.compiler\\.target>").matcher(content)
                if (compilerTargetMatcher.find()) {
                    return normalizeJavaVersion(compilerTargetMatcher.group(1).trim())
                }
                
                Matcher compilerReleaseMatcher = Pattern.compile("<maven\\.compiler\\.release>(.+?)</maven\\.compiler\\.release>").matcher(content)
                if (compilerReleaseMatcher.find()) {
                    return normalizeJavaVersion(compilerReleaseMatcher.group(1).trim())
                }
            }
        } catch (Exception e) {
            System.out.println("Não foi possível detetar a versão do Java no Maven, a usar o Java 17 por defeito")
        }
        
        return "17" // Fallback seguro
    }

    private static String normalizeJavaVersion(String version) {
        // Converte "1.8" para "8" para manter a compatibilidade com a action do GitHub
        if (version.startsWith("1.")) {
            return version.substring(2)
        }
        return version
    }

    private static String getGradleSteps(Path root) {
        String javaVersion = detectJavaVersionForGradle(root)
        
        return """
            - name: Set up JDK ${javaVersion}
              uses: actions/setup-java@v4
              with:
                java-version: '${javaVersion}'
                distribution: 'temurin'
                cache: gradle
            
            - name: Grant execute permission for gradlew
              run: if [ -f "gradlew" ]; then chmod +x gradlew; fi
            
            - name: Run Gradle Tests
              uses: nick-fields/retry@v3
              with:
                timeout_minutes: 30
                max_attempts: 3
                command: ./gradlew test
        """
    }

    private static String detectJavaVersionForGradle(Path root) {
        try {
            Path wrapperProps = root.resolve("gradle/wrapper/gradle-wrapper.properties")
            if (Files.exists(wrapperProps)) {
                String content = new String(Files.readAllBytes(wrapperProps))
                
                Matcher matcher = Pattern.compile("gradle-(\\d+)\\.").matcher(content)
                
                if (matcher.find()) {
                    int majorVersion = Integer.parseInt(matcher.group(1))
                    
                    if (majorVersion < 5) {
                        return "8"
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Não foi possível detetar a versão do Gradle, a usar o Java 17 por defeito")
        }
        
        return "17"
    }

    private static String getNodeSteps(boolean usesYarn) {
        if (usesYarn) {
            return """
            - name: Set up Node.js
              uses: actions/setup-node@v4
              with:
                node-version: 20
                cache: 'yarn'
            - name: Install dependencies
              run: yarn install --frozen-lockfile --ignore-engines
            - name: Run Yarn Tests
              uses: nick-fields/retry@v3
              with:
                timeout_minutes: 20
                max_attempts: 3
                command: yarn test
            """
        } else {
            return """
            - name: Set up Node.js
              uses: actions/setup-node@v4
              with:
                node-version: 20
            - name: Install dependencies
              run: npm ci --legacy-peer-deps || npm install --legacy-peer-deps
            - name: Run NPM Tests
              uses: nick-fields/retry@v3
              with:
                timeout_minutes: 20
                max_attempts: 3
                command: npm test
            """
        }
    }

    private static String getGoSteps() {
        return """
            - name: Set up Go
              uses: actions/setup-go@v4
              with:
                go-version: '1.20'
                cache: true
            - name: Install Dependencies
              run: go mod download
            - name: Run Go Tests
              uses: nick-fields/retry@v3
              with:
                timeout_minutes: 20
                max_attempts: 3
                command: go test -v ./...
        """
    }

    private static String getRustSteps() {
        return """
            - name: Set up Rust
              uses: actions-rs/toolchain@v1
              with:
                profile: minimal
                toolchain: stable
                override: true
            - name: Build
              run: cargo build --verbose
            - name: Run Cargo Tests
              uses: nick-fields/retry@v3
              with:
                timeout_minutes: 25
                max_attempts: 3
                command: cargo test --verbose
        """
    }
}