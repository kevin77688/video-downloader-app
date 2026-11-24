buildscript {
    // Security fixes for libraries used only by the build tools (the Android Gradle Plugin and its lint
    // tool), until AGP ships them itself. None of these end up in the APK. Constraints only set a
    // minimum: if AGP later brings a newer version, that one wins. Drop an entry once AGP resolves an
    // equal or newer version (`./gradlew buildEnvironment`, `./gradlew :app:dependencies`).
    val patchedBuildTools = listOf(
        "org.bouncycastle:bcprov-jdk18on:1.85",
        "org.bouncycastle:bcpkix-jdk18on:1.85",
        "org.bouncycastle:bcutil-jdk18on:1.85",
        "com.google.guava:guava:33.7.2-jre",
        "org.bitbucket.b_c:jose4j:0.9.6",
        "org.jdom:jdom2:2.0.6.1",
        "org.apache.commons:commons-lang3:3.18.0",
        "org.apache.httpcomponents:httpclient:4.5.14",
    )
    extra["patchedBuildTools"] = patchedBuildTools
    dependencies {
        constraints {
            patchedBuildTools.forEach { classpath(it) }
        }
    }
}

plugins {
    id("com.android.application") version "9.5.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

// The lint tool is resolved per module in its own configuration; apply the same minimum versions there.
@Suppress("UNCHECKED_CAST")
val patchedBuildTools = extra["patchedBuildTools"] as List<String>
subprojects {
    configurations.matching { it.name == "androidLintTool" }.configureEach {
        patchedBuildTools.forEach { dependencyConstraints.add(this@subprojects.dependencies.constraints.create(it)) }
    }
}
