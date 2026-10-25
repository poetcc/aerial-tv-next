import java.util.Properties
plugins { id("com.android.application") }
android {
    namespace = "app.orchard.sonybridge"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.dangbei.TVHomeLauncher"
        minSdk = 21
        targetSdk = 34
        versionCode = 6
        versionName = "0.3-history-revival"
    }
    val signingProps = rootProject.file("local/signing.properties")
    signingConfigs {
        if (signingProps.exists()) {
            val p = Properties().apply { signingProps.inputStream().use { load(it) } }
            create("aerial") {
                storeFile = rootProject.file("local/" + p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }
    buildTypes { release { signingConfig = signingConfigs.findByName("aerial") ?: signingConfigs.getByName("debug") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
