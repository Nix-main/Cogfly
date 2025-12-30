plugins {
    id("java")
    id("org.openjfx.javafxplugin") version("0.1.0")
    id("com.gradleup.shadow") version "9.3.0"

}

group = "dev.ambershadow"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("com.formdev:flatlaf:3.7")
    implementation("com.formdev:flatlaf-intellij-themes:3.7")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("net.harawata:appdirs:1.5.0")
    implementation("org.yaml:snakeyaml:2.2")
}

tasks.test {
    useJUnitPlatform()
}

javafx {
    version = "25"
    modules("javafx.controls", "javafx.swing")
}

tasks.shadowJar {
    archiveBaseName.set("cogfly")
    archiveClassifier.set("")
    archiveVersion.set("1.0")
    manifest {
        attributes["Main-Class"] = "dev.ambershadow.cogfly.Cogfly"
    }
}