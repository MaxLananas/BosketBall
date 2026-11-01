plugins {
    java
}

group = "fr.bosketball"
version = "2.6.2-ULTRA"
description = "Complete 3D Basketball Minigame Engine, Custom HUD, Ranks & Resource Pack for Paper 26.2"

repositories {
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
    testImplementation("junit:junit:4.13.2")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    filteringCharset = "UTF-8"
}
