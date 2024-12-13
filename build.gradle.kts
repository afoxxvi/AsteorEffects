import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.archivesName

plugins {
    kotlin("jvm") version "1.9.20"
    id("maven-publish")
}

group = "com.afoxxvi"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

tasks.register("copyJar") {
    doLast {
        val originalJarFile = File("${buildDir}/libs/${project.name}-${version}.jar")
        val destinationDir = File("C:/Minecraft/dev/jar")
        val renamedJarFile = File(destinationDir, "${project.name}-latest.jar")
        originalJarFile.copyTo(renamedJarFile, true)
    }
}

tasks {
    jar {
        finalizedBy("copyJar")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.afoxxvi"
            artifactId = "asteor-effects"
            version = "1.0-SNAPSHOT"
            from(components["java"])
        }
        //source
        create<MavenPublication>("source") {
            groupId = "com.afoxxvi"
            artifactId = "asteor-effects"
            version = "1.0-SNAPSHOT"
            artifact(tasks.kotlinSourcesJar.get())
        }
    }
    repositories {
        maven {
            mavenLocal()
        }
    }
}