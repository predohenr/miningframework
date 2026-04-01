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
        } else {
            Files.list(githubActionsDirectory).forEach { file ->
                String fileName = file.getFileName().toString().toLowerCase()
                if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
                    Files.delete(file)
                }
            }
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
              run: find . -type d -name ".git" -prune -o -name "*.java" -print | xargs javac
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
        
        if (ext == "go") return getGoSteps(root)
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
                # Instalamos as 3 principais ferramentas de teste/orquestração (muito rápido)
                pip install pytest nox tox
                if [ -f requirements.txt ]; then pip install -r requirements.txt; fi
                if [ -f pyproject.toml ]; then pip install .; fi
            - name: Run Tests
              uses: nick-fields/retry@v3
              with:
                timeout_minutes: 30
                max_attempts: 3
                # Lógica inteligente: usa o orquestrador correto se existir, senão usa pytest
                command: |
                  if [ -f noxfile.py ]; then 
                    nox
                  elif [ -f tox.ini ]; then 
                    tox
                  else 
                    pytest || python -m unittest discover
                  fi
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
                
                Matcher javaVersionMatcher = Pattern.compile("<java\\.version>(.+?)</java\\.version>").matcher(content)
                if (javaVersionMatcher.find()) return normalizeJavaVersion(javaVersionMatcher.group(1).trim())
                
                Matcher compilerTargetMatcher = Pattern.compile("<maven\\.compiler\\.target>(.+?)</maven\\.compiler\\.target>").matcher(content)
                if (compilerTargetMatcher.find()) return normalizeJavaVersion(compilerTargetMatcher.group(1).trim())
                
                Matcher compilerReleaseMatcher = Pattern.compile("<maven\\.compiler\\.release>(.+?)</maven\\.compiler\\.release>").matcher(content)
                if (compilerReleaseMatcher.find()) return normalizeJavaVersion(compilerReleaseMatcher.group(1).trim())

                Matcher pluginSourceMatcher = Pattern.compile("<source>(.+?)</source>").matcher(content)
                if (pluginSourceMatcher.find()) return normalizeJavaVersion(pluginSourceMatcher.group(1).trim())

                Matcher pluginTargetMatcher = Pattern.compile("<target>(.+?)</target>").matcher(content)
                if (pluginTargetMatcher.find()) return normalizeJavaVersion(pluginTargetMatcher.group(1).trim())
                
                Matcher pluginReleaseTagMatcher = Pattern.compile("<release>(.+?)</release>").matcher(content)
                if (pluginReleaseTagMatcher.find()) return normalizeJavaVersion(pluginReleaseTagMatcher.group(1).trim())
            }
        } catch (Exception e) {
            System.out.println("Can't detect Maven version, falling back to Java 11")
            return "11"
        }
        
        return "11" 
    }

    private static String normalizeJavaVersion(String version) {
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
                    } else if (majorVersion < 7) {
                        return "11"
                    }
                }
            } else {
                return "11" 
            }
        } catch (Exception e) {
            System.out.println("Can't detect Gradle version, using Java 11 by default")
            return "11"
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
                timeout_minutes: 30
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
                timeout_minutes: 30
                max_attempts: 3
                command: npm test
            """
        }
    }

    private static String getGoSteps(Path root) {
        String goVersionConfig = Files.exists(root.resolve("go.mod")) 
            ? "go-version-file: 'go.mod'" 
            : "go-version: 'stable'"

        return """
            - name: Set up Go
              uses: actions/setup-go@v5
              with:
                ${goVersionConfig}
                cache: true
            - name: Install Dependencies
              run: go mod download
            - name: Run Go Tests
              uses: nick-fields/retry@v3
              with:
                timeout_minutes: 30
                max_attempts: 3
                command: go test -v ./...
        """
    }

    private static String getRustSteps() {
        return """
            - name: Update Rust Toolchain
              run: rustup update stable && rustup default stable
            - name: Build
              run: cargo build --verbose
            - name: Run Cargo Tests
              uses: nick-fields/retry@v3
              with:
                timeout_minutes: 30
                max_attempts: 3
                command: cargo test --verbose
        """
    }
}