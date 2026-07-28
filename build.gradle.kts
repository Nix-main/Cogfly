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

val compileWinFolderPicker by tasks.register("compileWinFolderPicker") {
    doFirst { mkdir("${layout.buildDirectory.get()}/native") }
    doLast {
        try {
            providers.exec {
                commandLine(
                    "g++",
                    "-shared",
                    "-o",
                    "${layout.buildDirectory.get()}/native/winfolderpicker.dll",
                    "resources/libs/folderpicker.cpp",
                    "-lole32",
                    "-luuid"
                )
            }.result.get()
        } catch (e: Exception) {
            if (System.getProperty("os.name").contains("Windows", ignoreCase = true))
                throw e
        }
    }
}
val compileTinyFileDialogs = tasks.register("compileTinyFileDialogs") {
    doFirst { mkdir("${layout.buildDirectory.get()}/native") }
    doLast {
        try {
            providers.exec {
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
                isIgnoreExitValue = true
            }.result.get()
        } catch (e: Exception) {
            if (System.getProperty("os.name").contains("Windows", ignoreCase = true))
                throw e
        }
    }
}
tasks.register("ver") {
    doLast {
        println(project.version)
    }
}
tasks.processResources {
    dependsOn(compileWinFolderPicker, compileTinyFileDialogs)
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