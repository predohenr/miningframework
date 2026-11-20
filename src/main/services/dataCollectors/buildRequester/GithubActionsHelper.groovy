package services.dataCollectors.buildRequester

import groovy.json.JsonSlurper
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
            - name: Fetch git submodules
              run: git submodule update --init --recursive || echo "Could not fetch some submodules"
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
        if (ext == "py") return getPythonSteps(root)
        
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
                return getNodeSteps(usesYarn, detectNodeVersion(root))
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

    private static final List<Integer> PYTHON_MINORS = (8..15).toList()
    private static final int DEFAULT_PYTHON_MINOR = 10

    private static String getPythonSteps(Path root) {
        String python = "3.${detectPythonMinor(root)}"
        return """
            - name: Set up Python ${python}
              uses: actions/setup-python@v5
              with:
                python-version: '${python}'
            - name: Install dependencies
              run: |
                python -m pip install --upgrade pip
                pip install pytest
                if [ -f requirements.txt ]; then pip install -r requirements.txt || true; fi
                for f in requirements-dev.txt dev-requirements.txt requirements-test.txt test-requirements.txt requirements/test.txt requirements/dev.txt; do
                  if [ -f "\$f" ]; then pip install -r "\$f" || true; fi
                done
                if [ -f pyproject.toml ] || [ -f setup.py ]; then
                  pip install -e ".[test,tests,testing,dev]" || pip install -e ".[test,tests,testing]" || pip install -e . || pip install . || true
                fi
            - name: Run Tests
              uses: nick-fields/retry@v3
              with:
                timeout_minutes: 30
                max_attempts: 3
                shell: bash
                command: |
                  status=0
                  python -m pytest || status=\$?
                  if [ \$status -eq 5 ]; then
                    python -m unittest discover -v 2>&1 | tee unittest.log
                    status=\${PIPESTATUS[0]}
                    if grep -q "^Ran 0 tests" unittest.log; then echo "NO TESTS RAN"; exit 1; fi
                  fi
                  exit \$status
        """
    }

    static int detectPythonMinor(Path root) {
        try {
            Path pythonVersion = root.resolve(".python-version")
            if (Files.exists(pythonVersion)) {
                Matcher m = Pattern.compile("(?m)^\\s*(?:pypy)?3\\.(\\d+)").matcher(readText(pythonVersion))
                if (m.find()) {
                    int minor = Integer.parseInt(m.group(1))
                    return Math.min(Math.max(minor, PYTHON_MINORS.min()), PYTHON_MINORS.max())
                }
            }
            String spec = declaredPythonRequirement(root)
            if (spec != null) {
                List<Integer> allowed = PYTHON_MINORS.findAll { satisfiesRange(spec, [3, it, 0], [3, it, 999], false) }
                Integer chosen = preferredVersion(allowed, DEFAULT_PYTHON_MINOR)
                if (chosen != null) return chosen
            }
        } catch (Exception e) {
            System.out.println("Can't detect the Python version, using 3.${DEFAULT_PYTHON_MINOR}: ${e.message}")
        }
        return DEFAULT_PYTHON_MINOR
    }

    static String declaredPythonRequirement(Path root) {
        Path pyproject = root.resolve("pyproject.toml")
        if (Files.exists(pyproject)) {
            String text = readText(pyproject)
            Matcher m = Pattern.compile("(?m)^\\s*requires-python\\s*=\\s*[\"']([^\"']+)[\"']").matcher(text)
            if (m.find()) return m.group(1)
            Matcher poetry = Pattern.compile("(?ms)^\\s*\\[tool\\.poetry\\.dependencies\\]\\s*\$(.*?)(?:^\\s*\\[|\\z)").matcher(text)
            if (poetry.find()) {
                Matcher p = Pattern.compile("(?m)^\\s*python\\s*=\\s*[\"']([^\"']+)[\"']").matcher(poetry.group(1))
                if (p.find()) return p.group(1)
            }
        }
        Path setupCfg = root.resolve("setup.cfg")
        if (Files.exists(setupCfg)) {
            Matcher m = Pattern.compile("(?m)^\\s*python_requires\\s*=\\s*[\"']?([^\"'\\r\\n#]+)").matcher(readText(setupCfg))
            if (m.find()) return m.group(1).trim()
        }
        Path setupPy = root.resolve("setup.py")
        if (Files.exists(setupPy)) {
            Matcher m = Pattern.compile("python_requires\\s*=\\s*[\"']([^\"']+)[\"']").matcher(readText(setupPy))
            if (m.find()) return m.group(1)
        }
        return null
    }

    private static Integer preferredVersion(List<Integer> allowed, int preferred) {
        if (allowed.isEmpty()) return null
        if (allowed.contains(preferred)) return preferred
        List<Integer> newer = allowed.findAll { it > preferred }
        return newer ? newer.min() : allowed.max()
    }

    static boolean satisfiesRange(String spec, List<Integer> low, List<Integer> high, boolean npm) {
        String s = spec.trim()
        if (s.isEmpty() || s == "*" || s.equalsIgnoreCase("x")) return true
        return s.split("\\|\\|").any { String alternative ->
            String set = alternative.trim()
                    .replaceAll(/(\S+)\s+-\s+(\S+)/, '>=$1 <=$2')
                    .replaceAll(/(~=|===|==|!=|<=|>=|<|>|=|\^|~)\s+/, '$1')
            set.split(/[,\s]+/).findAll { !it.isEmpty() }.every { satisfiesClause(it, low, high, npm) }
        }
    }

    private static boolean satisfiesClause(String clause, List<Integer> low, List<Integer> high, boolean npm) {
        Matcher m = Pattern.compile("^(~=|===|==|!=|<=|>=|<|>|=|\\^|~)?v?(\\d+(?:\\.(?:\\d+|\\*|x|X))*)\$").matcher(clause)
        if (!m.matches()) return true
        String op = m.group(1) ?: "="
        List<String> raw = m.group(2).tokenize(".")
        List<Integer> parts = []
        for (String p : raw) {
            if (!(p ==~ /\d+/)) break
            parts << Integer.parseInt(p)
        }
        boolean wildcard = parts.size() < raw.size()
        List<Integer> floor = padVersion(parts, 0)
        List<Integer> ceiling = padVersion(parts, 999)
        switch (op) {
            case ">=":
                return compareVersions(high, floor) >= 0
            case ">":
                return compareVersions(high, npm || wildcard ? ceiling : floor) > 0
            case "<=":
                return compareVersions(low, ceiling) <= 0
            case "<":
                return compareVersions(low, floor) < 0
            case "!=":
                return wildcard ? !(compareVersions(floor, low) <= 0 && compareVersions(high, ceiling) <= 0) : true
            case "~=":
                if (parts.size() < 2) return true
                List<Integer> prefix = parts.subList(0, parts.size() - 1)
                return compareVersions(high, floor) >= 0 && compareVersions(low, padVersion(prefix, 999)) <= 0
            case "^":
                int i = parts.findIndexOf { it != 0 }
                if (i < 0) i = parts.size() - 1
                List<Integer> upper = parts.subList(0, i) + [parts[i] + 1]
                return compareVersions(high, floor) >= 0 && compareVersions(low, padVersion(upper, 0)) < 0
            case "~":
                List<Integer> next = parts.size() >= 2 ? [parts[0], parts[1] + 1] : [parts[0] + 1]
                return compareVersions(high, floor) >= 0 && compareVersions(low, padVersion(next, 0)) < 0
            default:
                return compareVersions(low, ceiling) <= 0 && compareVersions(high, floor) >= 0
        }
    }

    private static List<Integer> padVersion(List<Integer> parts, int filler) {
        List<Integer> v = new ArrayList<>(parts.size() > 3 ? parts.subList(0, 3) : parts)
        while (v.size() < 3) v << filler
        return v
    }

    private static int compareVersions(List<Integer> a, List<Integer> b) {
        for (int i = 0; i < 3; i++) {
            int c = a[i] <=> b[i]
            if (c != 0) return c
        }
        return 0
    }

    private static String readText(Path file) {
        return new String(Files.readAllBytes(file), Charset.forName("UTF-8"))
    }

    private static final List<Integer> INSTALLED_JDKS = [8, 11, 17, 21, 25]
    private static final int DEFAULT_JDK = 17
    private static final String GRADLE_TOOLCHAINS_FROM_ENV =
            INSTALLED_JDKS.collect { "JAVA_HOME_${it}_X64" }.join(",")

    private static String getJavaSetupSteps(int jdk, String cache) {
        List<Integer> order = INSTALLED_JDKS.findAll { it != jdk } + [jdk]
        String versions = order.collect { "                  ${it}" }.join("\n")
        return """
            - name: Set up JDK ${jdk}
              uses: actions/setup-java@v4
              with:
                distribution: 'temurin'
                cache: ${cache}
                java-version: |
${versions}
            - name: Remove settings.xml created by setup-java
              run: rm -f ~/.m2/settings.xml
"""
    }

    private static String getMavenSteps(Path root) {
        int jdk = detectJavaVersionForMaven(root)

        return getJavaSetupSteps(jdk, "maven") + """
            - name: Run Maven Tests
              uses: nick-fields/retry@v3
              with:
                timeout_minutes: 30
                max_attempts: 3
                command: mvn -B test
        """
    }

    static int detectJavaVersionForMaven(Path root) {
        try {
            String pom = new String(Files.readAllBytes(root.resolve("pom.xml")), Charset.forName("UTF-8"))
            pom = pom.replaceAll(/(?s)<!--.*?-->/, "")
            Map<String, String> props = mavenProperties(pom)

            List<String> candidates = []
            ["maven.compiler.release", "maven.compiler.source", "maven.compiler.target",
             "java.version", "java.release", "jdk.version", "javaVersion", "java.level"].each { key ->
                if (props.containsKey(key)) candidates << props[key]
            }
            String compilerConfig = mavenCompilerPluginConfiguration(pom)
            if (compilerConfig != null) {
                ["release", "source", "target"].each { tag ->
                    Matcher m = Pattern.compile("<${tag}>\\s*([^<]+?)\\s*</${tag}>").matcher(compilerConfig)
                    if (m.find()) candidates << m.group(1)
                }
            }

            Integer declared = null
            for (String c : candidates) {
                declared = parseJavaMajor(resolveMavenProperties(c, props))
                if (declared != null) break
            }

            Integer enforcerMin = null
            Matcher enforcer = Pattern.compile("(?s)<requireJavaVersion>.*?<version>\\s*([^<]+?)\\s*</version>").matcher(pom)
            if (enforcer.find()) {
                Matcher low = Pattern.compile("^[\\[(]?\\s*([^,\\])]+)").matcher(resolveMavenProperties(enforcer.group(1), props))
                if (low.find()) enforcerMin = parseJavaMajor(low.group(1))
            }

            int wanted = [declared, enforcerMin, detectJavaVersionFromVersionFiles(root)]
                    .findAll { it != null }.max() ?: DEFAULT_JDK
            return smallestInstalledJdkAtLeast(wanted)
        } catch (Exception e) {
            System.out.println("Can't detect the Java version of the Maven project, using Java ${DEFAULT_JDK}: ${e.message}")
            return DEFAULT_JDK
        }
    }

    private static Map<String, String> mavenProperties(String pom) {
        Map<String, String> props = [:]
        Matcher block = Pattern.compile("(?s)<properties>(.*?)</properties>").matcher(pom)
        while (block.find()) {
            Matcher p = Pattern.compile("<([A-Za-z0-9_.\\-]+)>\\s*([^<]*?)\\s*</\\1>").matcher(block.group(1))
            while (p.find()) props.putIfAbsent(p.group(1), p.group(2))
        }
        return props
    }

    private static String resolveMavenProperties(String value, Map<String, String> props) {
        String v = value
        for (int i = 0; i < 5 && v != null && v.contains('${'); i++) {
            v = v.replaceAll(/\$\{([^}]+)\}/) { String all, String name -> props.containsKey(name) ? props[name] : all }
        }
        return v
    }

    /** Text of every maven-compiler-plugin declaration (pluginManagement and plugins), or null. */
    private static String mavenCompilerPluginConfiguration(String pom) {
        StringBuilder blocks = new StringBuilder()
        int idx = pom.indexOf("<artifactId>maven-compiler-plugin</artifactId>")
        while (idx >= 0) {
            int end = pom.indexOf("</plugin>", idx)
            blocks.append(end < 0 ? pom.substring(idx) : pom.substring(idx, end)).append("\n")
            idx = pom.indexOf("<artifactId>maven-compiler-plugin</artifactId>", idx + 1)
        }
        return blocks.length() == 0 ? null : blocks.toString()
    }

    /** "1.8" -> 8, "17" -> 17, "21.0.2-tem" -> 21; anything else (e.g. "${x}", "src/test") -> null */
    static Integer parseJavaMajor(String raw) {
        if (raw == null) return null
        Matcher m = Pattern.compile("^(?:1\\.)?(\\d{1,2})(?:[.\\-+_].*)?\$").matcher(raw.trim())
        if (!m.matches()) return null
        int major = Integer.parseInt(m.group(1))
        return (major >= 5 && major <= 40) ? major : null
    }

    private static Integer detectJavaVersionFromVersionFiles(Path root) {
        try {
            Path javaVersion = root.resolve(".java-version")
            if (Files.exists(javaVersion)) {
                Integer v = parseJavaMajor(new String(Files.readAllBytes(javaVersion)).trim())
                if (v != null) return v
            }
            Path sdkmanrc = root.resolve(".sdkmanrc")
            if (Files.exists(sdkmanrc)) {
                Matcher m = Pattern.compile("(?m)^\\s*java\\s*=\\s*(\\S+)").matcher(new String(Files.readAllBytes(sdkmanrc)))
                if (m.find()) return parseJavaMajor(m.group(1))
            }
            Path toolVersions = root.resolve(".tool-versions")
            if (Files.exists(toolVersions)) {
                Matcher m = Pattern.compile("(?m)^\\s*java\\s+(?:[A-Za-z]+-)?(\\S+)").matcher(new String(Files.readAllBytes(toolVersions)))
                if (m.find()) return parseJavaMajor(m.group(1))
            }
        } catch (Exception ignored) { }
        return null
    }

    private static int smallestInstalledJdkAtLeast(int version) {
        return INSTALLED_JDKS.find { it >= version } ?: INSTALLED_JDKS.max()
    }

    private static String getGradleSteps(Path root) {
        String gradleVersion = detectGradleWrapperVersion(root)
        int jdk = detectJavaVersionForGradle(gradleVersion)
        boolean hasWrapper = Files.exists(root.resolve("gradlew"))
        String gradle = hasWrapper ? "./gradlew" : "gradle"
        int gradleMajor = gradleVersion == null ? 99 : Integer.parseInt(gradleVersion.tokenize(".")[0])
        // --no-build-cache: a corrupted remote build cache (e.g. Develocity) must not fail the build.
        // The toolchain property lets Gradle find every installed JDK for projects declaring toolchains.
        String options = (gradleMajor >= 4 ? " --no-build-cache" : "") +
                (gradleMajor >= 6 ? " -Porg.gradle.java.installations.fromEnv=${GRADLE_TOOLCHAINS_FROM_ENV}" : "")

        return getJavaSetupSteps(jdk, "gradle") + """
            - name: Grant execute permission for gradlew
              run: if [ -f "gradlew" ]; then chmod +x gradlew; fi

            - name: Run Gradle Tests
              uses: nick-fields/retry@v3
              with:
                timeout_minutes: 30
                max_attempts: 3
                command: ${gradle} test${options}
        """
    }

    static String detectGradleWrapperVersion(Path root) {
        try {
            Path wrapperProps = root.resolve("gradle/wrapper/gradle-wrapper.properties")
            if (!Files.exists(wrapperProps)) return null
            Matcher m = Pattern.compile("gradle-(\\d+\\.\\d+(?:\\.\\d+)?)").matcher(new String(Files.readAllBytes(wrapperProps)))
            return m.find() ? m.group(1) : null
        } catch (Exception ignored) {
            return null
        }
    }

    static int detectJavaVersionForGradle(String gradleVersion) {
        if (gradleVersion == null) return DEFAULT_JDK
        List<Integer> parts = gradleVersion.tokenize(".").collect { Integer.parseInt(it) }
        int major = parts[0], minor = parts.size() > 1 ? parts[1] : 0
        int v = major * 100 + minor
        int maxJava
        if (v >= 901) maxJava = 25
        else if (v >= 814) maxJava = 24
        else if (v >= 810) maxJava = 23
        else if (v >= 808) maxJava = 22
        else if (v >= 805) maxJava = 21
        else if (v >= 803) maxJava = 20
        else if (v >= 706) maxJava = 19
        else if (v >= 705) maxJava = 18
        else if (v >= 703) maxJava = 17
        else if (v >= 700) maxJava = 16
        else if (v >= 607) maxJava = 15
        else if (v >= 603) maxJava = 14
        else if (v >= 600) maxJava = 13
        else if (v >= 504) maxJava = 12
        else if (v >= 500) maxJava = 11
        else maxJava = 8
        return INSTALLED_JDKS.findAll { it <= maxJava }.max()
    }

    private static final List<Integer> NODE_MAJORS = (10..26).toList()
    private static final int DEFAULT_NODE = 20
    private static final Map<String, Integer> NODE_LTS_CODENAMES = [argon: 4, boron: 6, carbon: 8, dubnium: 10, erbium: 12,
                                                                    fermium: 14, gallium: 16, hydrogen: 18, iron: 20, jod: 22, krypton: 24]

    static String detectNodeVersion(Path root) {
        try {
            for (String file : [".nvmrc", ".node-version"]) {
                Path versionFile = root.resolve(file)
                if (Files.exists(versionFile)) {
                    String v = nodeVersionFromFile(readText(versionFile))
                    if (v != null) return v
                }
            }
            def engines = new JsonSlurper().parseText(readText(root.resolve("package.json")))?.engines
            if (engines instanceof Map && engines.node instanceof String) {
                String spec = engines.node
                List<Integer> allowed = NODE_MAJORS.findAll { satisfiesRange(spec, [it, 0, 0], [it, 999, 999], true) }
                Integer chosen = preferredVersion(allowed.findAll { it % 2 == 0 }, DEFAULT_NODE) ?: preferredVersion(allowed, DEFAULT_NODE)
                if (chosen != null) return chosen.toString()
            }
        } catch (Exception e) {
            System.out.println("Can't detect the Node.js version, using ${DEFAULT_NODE}: ${e.message}")
        }
        return DEFAULT_NODE.toString()
    }

    static String nodeVersionFromFile(String text) {
        String line = text.readLines().collect { it.replaceAll(/#.*/, "").trim() }.find { !it.isEmpty() }
        if (line == null) return null
        Matcher number = Pattern.compile("^v?(\\d+)(?:\\.(\\d+))?").matcher(line)
        if (number.find()) {
            return number.group(1) == "0" && number.group(2) != null ? "0.${number.group(2)}" : number.group(1)
        }
        Matcher lts = Pattern.compile("(?i)^lts/(.+)\$").matcher(line)
        if (lts.find()) {
            Integer major = NODE_LTS_CODENAMES[lts.group(1).toLowerCase()]
            return major != null ? major.toString() : "lts/*"
        }
        return line.toLowerCase() in ["node", "latest", "stable", "current"] ? "node" : null
    }

    private static String getNodeSteps(boolean usesYarn, String nodeVersion) {
        if (usesYarn) {
            return """
            - name: Set up Node.js ${nodeVersion}
              uses: actions/setup-node@v4
              with:
                node-version: '${nodeVersion}'
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
            - name: Set up Node.js ${nodeVersion}
              uses: actions/setup-node@v4
              with:
                node-version: '${nodeVersion}'
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

    private static String getGoSteps() {
        return """
            - name: Set up Go
              uses: actions/setup-go@v5
              with:
                go-version: 'stable'
                cache: true
            - name: Install Dependencies
              run: if [ -f go.mod ]; then go mod download; fi
            - name: Run Go Tests
              uses: nick-fields/retry@v3
              with:
                timeout_minutes: 30
                max_attempts: 3
                command: go test -vet=off ./...
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