# BeAR: Benchmarking in Augmented Reality
Android application for AR benchmarking using MTL models. Developed by Austin Funk for use in his Honors thesis in computer engineering at Brown University.
## Purpose
This app provides various benchmarking metrics for any PT or ONNX model (however, PT is not currently supported for multitask learning). Users can either upload individual images or use the device's camera as input, with any number of outputs displayed in tandem. Many different metrics and preferences are provided for ease of data collection. 
## Hardware Requirements
This app only works for Android devices with SDK>=26, though SDK=34+ is recommended.
## Usage
Before attempting to run the model, it is highly recommended that you peruse the settings and verify that everything is set up as you want it. Once all settings are to your liking, simply press the image icon in the bottom right corner of your screen. To view the model input and output(s) along with recorded metrics, simply swipe to navigate different views. Responsiveness is dependent on model performance and postprocessing speeds.
The settings menu can be accessed via the three dots in the top right hand corner of the home page. The navigation accessible via the top left corner should not be used at this time, and users should remain on the "Upload" page regardless of if they are uploading their input image or using the device's camera.
Within the settings, there are currently three menus: Metrics, Camera, and Model.
### Metrics
The various metrics should be understandable purely from their name and units. Some metrics, including (but not limited to) memory usage, battery usage, power consumption, and temperature change, should only be considered over large sample sizes, where large is considered minimally 10 seconds.
### Camera
The camera menu allows you to enable/disable the device's camera as well as other key settings, and therefore **should not be skipped regardless of whether you plan to use the camera or not.**
Resolution refers to the *input resolution required by the model*. The default resolution of (224, 224) is common for most models.
The metric limiters determine when metrics should be recorded, allowing for more accurate benchmarking in the areas described previously. *This is still in development.*
### Model
Most settings here should be straightforward. **It is essential that all outputs are mapped to a postprocessing method, as the default method does not currently work for ONNX models.** These outputs, as far as I'm aware, are case sensitive and should match **exactly** with the output dictionary produced by the model in, say, a Python environment.
## TODO
* Securely log metric data in CSV
* Upload models
* Create .apk
