plugins {
    kotlin("jvm") version "2.3.10"
    // 1. ShadowJar 플러그인 추가 (현재 가장 안정적인 버전)
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.github.mhstar331"
version = "1.0"

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

// 2. ShadowJar 설정
tasks.shadowJar {
    archiveFileName.set("Lucky_Box-${project.version}.jar")
    mergeServiceFiles()
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    finalizedBy("copyToServer")
}

// 3. 서버로 복사하는 작업 (shadowJar 파일을 복사하도록 변경)
tasks.register<Copy>("copyToServer") {
    dependsOn("shadowJar")
    from(layout.buildDirectory.file("libs/Lucky_Box-${project.version}.jar"))
    into("C:/Users/USER/Desktop/마크 관련/럭키박스 서버/plugins")
}

// 4. 빌드 버튼 누르면 자동으로 복사까지 수행
tasks.build {
    finalizedBy("copyToServer")
}