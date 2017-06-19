# Visualize #

### Structure of the repository ###

* Directory `Visualize` contains the Android app's source code
* Directory `Models` contains the files obtained from the fine-tuning of the three CNN using the Caffe framework for the object recognition task
* Directory `caffe-android-cpp` contains the file that has been changed from the original GitHub project used for porting the Caffe framework to Android (more on this below)

### A few notes regarding the Android porting of Caffe ###

* The libraries for each ABI were built using the following GitHub project: https://github.com/sh1r0/caffe-android-lib
* The only file that was changed from the source project is the file `caffe_jni.cpp` located in the directory `/caffe/android/` of the [porting project](https://github.com/sh1r0/caffe-android-lib); this file contains the native C++ methods called from the JNI: the only changes made to this file consisted in editing the methods' names so that they would match with the fully-qualified class name of our project and changing the return type of a method. We have thus decided to include in this repository only the edited `caffe_jni.cpp` file since the remaining parts have not been touched, hence it is more practical to clone the original project from the link reported above and then just replace the `caffe_jni.cpp` file with the one found in this repo in case you are interested in running it to build the libraries.
* After building the libraries as described in the [porting](https://github.com/sh1r0/caffe-android-lib)'s readme, the resulting `.so` files (namely `libcaffe.so` and `libcaffe_jni.so`) will be found under the porting's directory `/android_lib/caffe/lib/`: we have built the libraries for a list of ABI (i.e. `armeabi`, `armeabi-v7a-hard-softfp with NEON`, `arm64-v8a`, `x86`) and then proceeded to place each of them inside a proper directory (named after the ABI's name) located in our project's directory `app/src/main/jniLibs`
* Since the libraries have been built externally and are the only thing that has been imported in our project, the Android Studio IDE gives some warnings in the `CaffeMobile.java` file where all the corresponding JNI calls are declared, complaining that the given JNI method could not be resolved. We have tried to integrate the source files of the porting inside Android Studio without any luck, hence we decided to suppress these warnings since everything is working fine by just importing the resulting `.so` files, built for each ABI, inside the `jniLibs` directory.

### Dataset ###
The dataset used for the fine-tuning of the CNN with the Caffe framework was collected from www.image-net.org and is available for download at the following link: https://drive.google.com/open?id=0Bxi-mkaZwiBmb0cyYUo0UTJXVzA

This dataset includes about 50k images representing 34 classes of objects, which define the kind of objects that our model can recognise. The dataset is already split between training and validation images, for which we have used the 80-20 rule (i.e. for each class, retain 80% of the images for the training process and use the remaining 20% for the validation). The complete list of the classes of objects can be found in the file `synset_words_EN.txt` located at `Visualize/app/src/main/assets/`

### Training the Nets ###
The Convolutional Neural Networks that we chose to train for the object recognition task (GoogLeNet, Network-in-Network, SqueezeNet) have been trained using the [Caffe Framework](https://github.com/BVLC/caffe) with the parameters sepcified in the files included in the `Models` directory.  
The training was carried out on PC with the following specs:

**OS**: Ubuntu 15.10  
**CPU**: Intel i5 2500k @ 4.2 GHz  
**GPU**: Nvidia GTX 560 Ti 1GB VRAM  
**RAM**: 8GB

Moreover, the training ran entirely on the GPU.

### Resulting models performances ###
We report the top-1 accuracy scores of the resulting models obtained by fine-tuning the CNNs included in the `Models` directory; these scores were obtained by testing the models on the validation images of the dataset.

|Model             |Top-1 Accuracy|
|------------------|--------------|
|Network-in-Network|65%           |
|GoogLeNet         |77%           |
|SqueezeNet_v1.1   |67%           |

### Testing Device ###
The main device used for testing purposes throughout the whole development of the application is a **OnePlus 3T** running on Android 7.1.1.
