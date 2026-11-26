plugins {
    java
}

group = "io.github.earlgalvan"
version = "1.1.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Built against the oldest supported API (1.18.2). -PpaperApi=<version> compiles
    // against another one to check that every call still exists there.
    val paperApi = (findProperty("paperApi") as String?) ?: "1.18.2-R0.1-SNAPSHOT"
    compileOnly("io.papermc.paper:paper-api:$paperApi")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

configurations.compileClasspath {
    attributes.attribute(
        TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
        ((findProperty("javaRelease") as String?) ?: "17").toInt()
    )
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:removal"))
        // Paper 1.18.2+ runs on Java 17 or newer. -PjavaRelease is only for the API check builds.
        options.release.set(((findProperty("javaRelease") as String?) ?: "17").toInt())
    }
    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
}
