# ONNX Runtime uses JNI/reflection. Keep its public Java API for release builds.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
