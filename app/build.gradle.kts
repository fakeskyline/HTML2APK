plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.fakeskyline.html2apk"; compileSdk = 36
 defaultConfig { applicationId = "com.fakeskyline.html2apk"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "0.1.0" }
}
dependencies { implementation("androidx.activity:activity-ktx:1.9.3"); implementation("com.android.tools.build:apksig:8.9.2") }
tasks.register("copyTemplateApk", Copy) { dependsOn(":template:assembleRelease"); from(project(":template").layout.buildDirectory.dir("outputs/apk/release")); include("template-release-unsigned.apk"); into(layout.projectDirectory.dir("src/main/assets")); rename { "template.apk" } }
tasks.named("preBuild") { dependsOn("copyTemplateApk") }
