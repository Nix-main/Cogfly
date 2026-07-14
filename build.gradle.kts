plugins {
    id("java")
    id("com.gradleup.shadow") version "9.3.0"
}

group = "dev.ambershadow"
version = property("version") as String

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
    commandLine(
        "g++",
        "-shared",
        "-o",
        "${layout.buildDirectory.get()}/native/winfolderpicker.dll",
        "libs/folderpicker.cpp",
        "-lole32",
        "-luuid"
    )
}

val compileTinyFileDialogs by tasks.registering(Exec::class) {
    workingDir = projectDir
    commandLine(
        "gcc",
        "-shared",
        "-o",
        "${layout.buildDirectory.get()}/native/wintinyfiledialogs.dll",
        "libs/tinyfiledialogs.c",
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
val compileNative by tasks.registering {
    mkdir("${layout.buildDirectory.get()}/native")
    dependsOn(compileWinFolderPicker, compileTinyFileDialogs)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-preview")
}

if (System.getProperty("os.name").lowercase().contains("windows")) {
    tasks.processResources {
        dependsOn(compileNative)
    }
}

tasks.shadowJar {
    archiveBaseName.set("Cogfly")
    archiveClassifier.set("")
    archiveVersion.set(version.toString())
    manifest {
        attributes["Main-Class"] = "dev.ambershadow.cogfly.Cogfly"
        attributes["Implementation-Version"] = version
    }
}