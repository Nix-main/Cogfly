plugins {
    id("java")
    id("application")
    id("com.gradleup.shadow") version "9.3.0"
}

group = "dev.ambershadow"
version = property("version") as String

// Java 22 minimum since since project uses unnamed vars/patterns
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(22))
    }
}

tasks.withType<JavaCompile> {
    options.release.set(22)
}

// provides us the 'run' gradlew task
application {
    mainClass.set("dev.ambershadow.cogfly.Cogfly")
}

repositories {
    mavenCentral()
}

sourceSets {
    main {
        resources.srcDir(layout.buildDirectory.dir("native"))
    }
}

dependencies {
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("com.formdev:flatlaf:3.7")
    implementation("com.formdev:flatlaf-intellij-themes:3.7")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("net.harawata:appdirs:1.5.0")
    implementation("ch.qos.logback:logback-classic:1.5.37")
    implementation("com.formdev:svgSalamander:1.1.4")
    implementation("org.yaml:snakeyaml:2.2")
}

val compileWinFolderPicker by tasks.registering(Exec::class) {
    workingDir = projectDir
    doFirst { mkdir("${layout.buildDirectory.get()}/native") }
    commandLine(
        "g++",
        "-shared",
        "-o",
        "${layout.buildDirectory.get()}/native/winfolderpicker.dll",
        "resources/libs/folderpicker.cpp",
        "-lole32",
        "-luuid"
    )
}

val compileTinyFileDialogs by tasks.registering(Exec::class) {
    doFirst { mkdir("${layout.buildDirectory.get()}/native") }
    workingDir = projectDir
    commandLine(
        "gcc",
        "-shared",
        "-o",
        "${layout.buildDirectory.get()}/native/wintinyfiledialogs.dll",
        "resources/libs/tinyfiledialogs.c",
        "-lole32",
        "-lcomdlg32",
        "-lshell32",
        "-luuid"
    )
}

tasks.register("ver") {
    doLast {
        println(project.version)
    }
}

tasks.shadowJar {
    archiveBaseName.set("Cogfly")
    archiveClassifier.set("SHADED") // convention
    archiveVersion.set(version.toString())

    manifest {
        attributes["Main-Class"] = "dev.ambershadow.cogfly.Cogfly"
        attributes["Implementation-Version"] = version
    }
}

// satiate the machine... 
tasks.named("startShadowScripts") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named("startScripts") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named("distTar") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named("distZip") {
    dependsOn(tasks.named("shadowJar"))
}