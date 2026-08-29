plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
}

/**
 * Compiles and tests the *real* `com.dadsvictory.domain` sources from the app
 * module on a plain JVM — no Android SDK, no emulator.
 *
 * The domain package contains no `android.*` or `androidx.*` imports precisely so
 * that this is possible: the streak arithmetic, money estimates, achievement
 * rules, rotation engine and encrypted backup format can all be verified for real
 * on any machine, including CI, in a couple of seconds.
 *
 * Run with:  ./gradlew -p tools/logic-verify test
 */
kotlin {
    // No toolchain pin: this harness only has to compile the domain sources on
    // whatever JDK is present. The Android module pins Java 17 separately.
    sourceSets["main"].kotlin.setSrcDirs(listOf("../../app/src/main/java/com/dadsvictory/domain"))
    sourceSets["test"].kotlin.setSrcDirs(listOf("src/test/kotlin"))
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}

/**
 * Fails the build if anything in the domain package starts importing Android,
 * which would silently break the ability to verify it here.
 */
val checkDomainIsPure by tasks.registering {
    val domainDir = file("../../app/src/main/java/com/dadsvictory/domain")
    doLast {
        val offenders = domainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .mapNotNull { file ->
                val bad = file.readLines().filter { line ->
                    val t = line.trim()
                    t.startsWith("import android.") || t.startsWith("import androidx.")
                }
                if (bad.isEmpty()) null else "${file.name}: ${bad.joinToString("; ")}"
            }
            .toList()
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "The domain package must stay free of Android imports so it can be verified " +
                    "on the JVM. Found:\n" + offenders.joinToString("\n"),
            )
        }
    }
}

tasks.named("test") { dependsOn(checkDomainIsPure) }
