plugins {
    kotlin("jvm") version "1.8.0"
}

group = "de.fiereu"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    // ByteBuddy
    implementation("net.bytebuddy:byte-buddy:1.14.4")
    implementation("net.bytebuddy:byte-buddy-agent:1.14.4")

    // PokeMMO
    implementation(files("libs/PokeMMO.jar"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

// create task which runs the Snooper.kt main function and sets its working directory to "C:\Program Files\PokeMMO"
val runSnooper = tasks.register<JavaExec>("runSnooper") {
    mainClass.set("SnooperKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = file("C:\\Program Files\\PokeMMO")
}