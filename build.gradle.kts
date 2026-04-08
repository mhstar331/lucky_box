plugins {
    kotlin("jvm") version "2.3.10"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("lucky_box")
    archiveClassifier.set("")
    archiveVersion.set("")
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

tasks.register<Copy>("copyToServer") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().archiveFile)
    into("C:/Users/USER/Desktop/마크 관련/럭키박스 서버/plugins")
}

tasks.build {
    finalizedBy("copyToServer")
}

