# Monitor
The majority of computation (including model inference) takes place within this folder.
## ProcessingResultListener
This interface is only necessary for `HardwareMonitor` subclasses that allow for pipelining or other non-sequential processing. This allows 
for communication between `MTLBox` and `HardwareMonitor` objects.
## MTLBox
`MTLBox` serves as an abstraction between the UI and model processing. The most important thing to consider currently is where processing takes place 
on the device since there isn't currently a way to do this via the settings menu. `createSession(File file, OrtEnvironment env)` processes the model 
on the CPU and `createNNSession(File file, OrtEnvironment env)` processes the model on the NPU. Unfortunately, the code for split execution is 
separated via a bloated `if` statement in `public MTLBox(Settings settings, Context context, MainActivity mainActivity)`, so changing the code between
CPU and NPU execution is slightly more of a headache than it ought to be. This is something that should be changed expeditiously.
## HardwareMonitor
This file is a bit messier than most of the others. The main sections to care about are `PyTorchModelMonitor` and `SplitModelMonitor`. `PyTorchModelMonitor` 
technically supports PyTorch models, but is more a misnomer at this point in development since ONNX models are very preferred. Both of them have a very similar 
structure: `startExecuteAndMonitor()` is called at the beginning of a metrics recording cycle, `runInference(...)` is called every frame, then 
`finishExecuteAndMonitor()` returns final metrics data. Most of the other helpers aren't of much importance, and the easiest way to deal with them is to 
collapse them in your preferred IDE (such as Android Studio) to make navigation more manageable.
