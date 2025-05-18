# C++ Integration
Currently, all C++ code is in `native-lib.cpp`. The following line in the CMake is what imports it into the project. If more files are added, 
the CMake should be changed.
- `add_library(native-lib SHARED cpp/native-lib.cpp)`

The CMake is currently in the `main` folder within `src` rather than being nested deeper into `cpp`. If this is changed, the function name syntax will have to change.

## Defining Functions
The easiest strategy for adding new functions is to copy and edit an existing function. Below are the main rules to keep in mind when defining a new function.
### Naming
The following two lines should be included before each function definition:

```
extern "C"
JNIEXPORT void JNICALL
```
Defining the function name depends on the relative path from the CMake file to the function definition in Java. For example, 
a function called `cppExample` used in `ImageConversionUtil` would have the following name:
- `Java_com_example_arbenchapp_util_ImageConversionUtil_cppExample`

Note that locations in the path are separated using underscores (`_`) rather than the typical slashes or periods.

The first two arguments for the C++ function should be as follows. These should not exist in the Java function.
```
JNIEnv *env,
jclass clazz,
...
```
More details can be found in the next section for definitions in Java.
## Including C++ in Java via JNI
Any class that uses C++ code needs to import it using the following syntax. Note the lack of `.cpp` in the name:
```
static {
    System.loadLibrary("native-lib");
}
```
To be able to use functions from the imported C++ library, they must be defined using the following syntax:
```
private static native void cppExample();
```
The types used in Java, such as `int` and `float`, correspond to special types in C++. This applies to the arguments as well. 
Below is a table comparing some of the common types in Java to C++.
|Java Type|C++ Type|
|:---:|:---:|
|`int`|`jint`|
|`Bitmap`|`jobject`|
|`float[]`|`jfloatArray`|
|`float[][]`|`jobjectArray`|

Consulting JNI documentation will likely be necessary for more complex functions.
