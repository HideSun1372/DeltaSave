plugins {
    java
}

group = "com.hidesun1372.deltasave"
version = "1.2"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}