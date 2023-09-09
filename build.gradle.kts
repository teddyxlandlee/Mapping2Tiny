import org.tukaani.xz.*

buildscript {
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.2.2")
        classpath("org.tukaani:xz:1.9")
    }
}

plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.cadixdev.licenser") version "0.6.1"
}

group = "xland.ioutils"
version = project.property("app_version")!!

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net")
}

dependencies {
    implementation("net.fabricmc:mapping-io:0.4.2")
}

java.withSourcesJar()

tasks.withType(JavaCompile::class).configureEach {
    options.release.set(11)
}

tasks.processResources {
    from("LICENSE.txt") {
        rename { "LICENSE_${project.name}.txt" }
    }
}

tasks.jar {
    manifest.attributes("Main-Class" to "xland.ioutils.mapping2tiny.Main")
}

tasks.shadowJar {
    archiveClassifier.set("shadow")
    minimize()
//    mergeServiceFiles()
    relocate("net.fabricmc.mappingio", "xland.ioutils.mapping2tiny.mio")
}

val proguardOutput = buildDir.resolve("libs/${project.name}-${project.version}-pro.jar")
val mappingXz = proguardOutput.resolveSibling("${project.name}-${project.version}.mapping.xz")
tasks.register("proguardJar", proguard.gradle.ProGuardTask::class) {
    dependsOn(tasks.shadowJar)
    injars(tasks.shadowJar)
    outjars(proguardOutput)
    libraryjars("${System.getProperty("java.home")}/jmods")
    configuration("conf.pro")
    val mapping = proguardOutput.resolveSibling("${project.name}-${project.version}-pro.mapping")
    printmapping(mapping)
    doLast {
        mappingXz.outputStream().buffered().let { XZOutputStream(it, LZMA2Options()) }.use { output ->
            mapping.inputStream().buffered().use { input ->
                input.copyTo(output)
            }
        }
    }
}

tasks.register("slimJar", Zip::class) {
    dependsOn("proguardJar")
    from(zipTree(proguardOutput))
    from(mappingXz) {
        into("META-INF/")
    }

    archiveExtension.set("jar")
    destinationDirectory.set(buildDir.resolve("libs"))
    archiveClassifier.set("slim")
}

license {
    charset("utf-8")
    header("HEADER.txt")
    include("**/*.java")
}

tasks.build {
    dependsOn("slimJar")
}
