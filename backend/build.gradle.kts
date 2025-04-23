plugins {
    id("java")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation("org.json:json:20230618")
    testImplementation("junit:junit:4.13.2")
}