plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

group = "dev.kiddo"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.18.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

paperweight.reobfArtifactConfiguration =
    io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

tasks.jar {
    manifest {
        attributes["Main-Class"] = "dev.kiddo.visualwand.VisualWand"
    }
}

tasks.processResources {
    expand("version" to version)
}

tasks {
    runServer {
        minecraftVersion("1.21.11")
        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.runServer {
    doFirst {
        val cfg = runDirectory.get().asFile.resolve("plugins/bStats/config.yml")
        if (!cfg.exists()) {
            cfg.parentFile.mkdirs()
            cfg.writeText("enabled: false\n")
        }
    }
}
