plugins {
    id("java")
    id("com.gradleup.shadow") version "9.3.0"

}

group = "dev.ambershadow"
version = "1.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("com.formdev:flatlaf:3.7")
    implementation("com.formdev:flatlaf-intellij-themes:3.7")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("net.harawata:appdirs:1.5.0")
    implementation("ch.qos.logback:logback-classic:1.5.6")
    implementation("com.formdev:svgSalamander:1.1.4")
    implementation("org.yaml:snakeyaml:2.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-preview")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("cogfly")
    archiveClassifier.set("")
    archiveVersion.set("" + version)
    manifest {
        attributes["Main-Class"] = "dev.ambershadow.cogfly.Cogfly"
    }
}