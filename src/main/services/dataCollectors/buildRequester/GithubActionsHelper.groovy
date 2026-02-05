package services.dataCollectors.buildRequester

import project.Project
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

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
        
        if (ext == "py") {
            return getPythonSteps()
        }

        if (ext == "java") {
            if (Files.exists(root.resolve("pom.xml"))) {
                return getMavenSteps()
            }
            if (Files.exists(root.resolve("build.gradle")) || Files.exists(root.resolve("build.gradle.kts"))) {
                return getGradleSteps()
            }
            return """
            - name: Set up JDK 17
              uses: actions/setup-java@v3
              with:
                java-version: '17'
                distribution: 'temurin'
            - name: Compile Java File
              run: javac **/*.java
            """
        }

        if (ext == "js" || ext == "ts" || ext == "jsx" || ext == "tsx") {
            if (Files.exists(root.resolve("package.json"))) {
                boolean usesYarn = Files.exists(root.resolve("yarn.lock"))
                return getNodeSteps(usesYarn)
            }
            // Fallback: Syntax Check
            return """
            - name: Set up Node.js
              uses: actions/setup-node@v3
              with:
                node-version: 16
            - name: Run Node Check
              run: node --check ./**/*.js
            """
        }
        
        if (ext == "go") {
             return getGoSteps()
        }

        if (ext == "rs") {
             return getRustSteps()
        }

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
            
            - name: Run Tests (Retry 3x)
              uses: nick-fields/retry@v3
              with:
                timeout_minutes: 20
                max_attempts: 3
                command: pytest || python -m unittest discover
        """
    }

    private static String getMavenSteps() {
        return """
            - name: Set up JDK 11
              uses: actions/setup-java@v3
              with:
                java-version: '11'
                distribution: 'temurin'
                cache: maven
            
            - name: Run Maven Tests
              uses: nick-fields/retry@v3
              with:
                timeout_minutes: 30
                max_attempts: 3
                command: mvn -B test -DfailIfNoTests=false
        """
    }

    private static String getGradleSteps() {
        return """
            - name: Set up JDK 11
              uses: actions/setup-java@v3
              with:
                java-version: '11'
                distribution: 'temurin'
                cache: gradle
            
            - name: Grant execute permission for gradlew
              run: chmod +x gradlew
            
            - name: Run Gradle Tests
              uses: nick-fields/retry@v3
              with:
                timeout_minutes: 30
                max_attempts: 3
                command: ./gradlew test
        """
    }

    private static String getNodeSteps(boolean usesYarn) {
        if (usesYarn) {
            return """
            - name: Set up Node.js
              uses: actions/setup-node@v3
              with:
                node-version: 16
                cache: 'yarn'
            - name: Install dependencies (Yarn)
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
              uses: actions/setup-node@v3
              with:
                node-version: 16
                cache: 'npm'
            - name: Install dependencies (NPM)
              run: npm ci --legacy-peer-deps || npm install --legacy-peer-deps
            - name: Run NPM Tests
              uses: nick-fields/retry@v3
              with:
                timeout_minutes: 20
                max_attempts: 3
                command: npm test -- --passWithNoTests
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
            
            - name: Run Go Tests (Retry 3x)
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
            
            - name: Run Cargo Tests (Retry 3x)
              uses: nick-fields/retry@v3
              with:
                timeout_minutes: 25
                max_attempts: 3
                command: cargo test --verbose
        """
    }
}