import org.jetbrains.kotlin.konan.properties.Properties

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}

// Use an integer for version numbers
version = 1

cloudstream {
    // All of these properties are optional, you can safely remove any of them.

    description = "English movies and TV shows."
    authors = listOf("Gl1tch95")

    /**
    * Status int as one of the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta-only
    **/
    status = 1 // Will be 3 if unspecified

    tvTypes = listOf(
      "Movie",
      "TvSeries",
    )

    requiresResources = true
    // Random CC logo I found
    iconUrl = "https://raw.githubusercontent.com/Gl1tch95/NoMoreSnow/heads/master/Aanglamu/icon.png"
}

android {
    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())

        buildConfigField(
            "String",
            "TMDB_KEY",
            "\"${properties.getProperty("TMDB_KEY")}\""
        )
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}
