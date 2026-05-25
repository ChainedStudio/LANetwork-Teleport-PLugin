import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

// 1. Generate a dynamic timestamp string (YearMonthDay_HourMinute)
val buildTimestamp: String = SimpleDateFormat("yyyyMMdd_HHmm").format(date())

// Helper function to get a clean Date instance
fun date() = Date()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    runServer {
        minecraftVersion("1.21.11")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    // 2. Configure the jar task to use the dynamic name format
    jar {
        archiveFileName.set("${rootProject.name}-${project.version}-$buildTimestamp.jar")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}