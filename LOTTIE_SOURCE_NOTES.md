# Lottie integration reference

Airbnb's official Lottie Android release page lists **6.7.1** as the latest release checked for this integration: https://github.com/airbnb/lottie-android/releases

The official Android guide documents the Maven coordinate `com.airbnb.android:lottie`, recommends `app:lottie_rawRes` for animations in `res/raw`, and shows `app:lottie_loop` and `app:lottie_autoPlay` on `LottieAnimationView`: https://github.com/airbnb/lottie/blob/master/android.md

Moonix uses the bundled `res/raw` JSON approach and pins Lottie Android to 6.7.1.
