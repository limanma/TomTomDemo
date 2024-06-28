plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val tomtomApiKey: String by project

android {
    namespace = "com.example.tomtomdemo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.tomtomdemo"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes.configureEach {
        buildConfigField("String", "TOMTOM_API_KEY", "\"$tomtomApiKey\"")
    }

    packaging {
        jniLibs.pickFirsts.add("lib/**/libc++_shared.so")
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.5.3")
    implementation("androidx.navigation:navigation-ui-ktx:2.5.3")
    val version = "1.5.0"
    implementation("com.tomtom.sdk.location:provider-android:$version")
    implementation("com.tomtom.sdk.location:provider-map-matched:$version")
    implementation("com.tomtom.sdk.location:provider-simulation:$version")
    implementation("com.tomtom.sdk.maps:map-display:$version")
    implementation("com.tomtom.sdk.navigation:navigation-online:$version")
    implementation("com.tomtom.sdk.navigation:ui:$version")
    implementation("com.tomtom.sdk.routing:route-planner-online:$version")
    implementation("com.tomtom.sdk.search:search-online:$version")
    implementation("com.tomtom.sdk.search:ui:$version")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}