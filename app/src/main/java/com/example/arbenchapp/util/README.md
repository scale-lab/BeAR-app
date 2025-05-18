# Utilities
BeAR currently has three main utilities, with two of them being very linked. `CameraUtil` is fairly standalone and won't require much (if any) customization.
## Conversion
`ConversionUtil` links directly with `ImageConversionUtil` to abstract image postprocessing away from the `HardwareMonitor`. The addition of any new image 
conversion methods also requires changes to at least one `switch` statement in `ConversionUtil`. The README in `cpp` discusses using JNI in more depth in case 
you would like to utilize C++ for image processing.
