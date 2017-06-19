# Visualize #

### Structure of the archive ###

* Directory `Visualize` contains the Android app's source code
* Directory `Models` contains the files obtained from the training of the three CNN using the Caffe framework for the object recognition task
* Directory `caffe-android-lib` contains the file that has been changed from the original GitHub project used for porting the Caffe framework to Android (more on this below)

### A few notes regarding the Android porting of Caffe ###

* The libraries for each ABI were built using the following GitHub project: https://github.com/sh1r0/caffe-android-lib
  * The only file that was changed from the source is the file `caffe_jni.cpp` located in `caffe-android-lib/caffe/android/` containing the native C++ methods called from the JNI: we modified the names of the methods so that they would match with the fully-qualified class name of our project, as well as deleting unused methods; we have decided to include in the final zip only the single file that has been changed because the whole porting utility is almost 1 GB big, hence it is more practical to download it from the link reported above and then just change this single file in case you are interested in running it.
  * After building the libraries as described in the project's readme, the resulting `.so` files (namely `libcaffe.so` and `libcaffe_jni.so`) will be found under the directory `caffe-android-lib/android_lib/caffe/lib/`: we have built these files for a list of ABI (i.e. `armeabi`, `armeabi-v7a-hard-softfp with NEON`, `arm64-v8a`, `x86`) and we placed each library inside the project's directory `app/src/main/jniLibs`
  * Since the libraries are prebuilt and are the only thing that has been imported in the project, the Android Studio IDE gives some warnings in the `CaffeMobile.java` file where all the corresponding JNI calls are declared, saying that the given JNI method could not be resolved: we have tried to include the external source files required for building the library without any luck, and since everything is working fine by just importing the resulting `.so` files in the jniLibs directory, we simply decided to suppress these warnings.