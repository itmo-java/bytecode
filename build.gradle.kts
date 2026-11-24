plugins {
    id("java")
    id("application")
}

group = "org.example"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

application {
    mainClass = "org.example.Main"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.ow2.asm:asm:9.11.1")
    implementation("org.ow2.asm:asm-tree:9.11.1")
    implementation("org.ow2.asm:asm-analysis:9.11.1")
    implementation("org.ow2.asm:asm-util:9.11.1")
    implementation("com.google.code.gson:gson:2.13.2")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "512m"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

// The supplied archives are analysis inputs, not application resources.
tasks.processResources {
    exclude("*.jar", "*.kt")
}

tasks.register<JavaExec>("sampleReports") {
    group = "application"
    description = "Generate JSON reports for all three supplied JAR files"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "org.example.SampleReports"
    args("src/main/resources", "reports")
}
