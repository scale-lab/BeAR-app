# BeAR: Benchmarking in Augmented Reality<img align="right" width="15%" src="img/BeAR.png">
Android application for AR benchmarking using MTL models. Developed by Austin Funk as part of his Honors thesis in computer engineering at Brown University.
## 🔍 Introduction
BeAR provides various benchmarking metrics for Multi-Task Learning (MTL) models. The purpose of this app is to provide flexible testing and benchmarking on edge devices without requiring extensive dependencies, such as Google's ARCore. Specific runtime improvements have been implemented as well, notably support for pipelined execution of the encoder and decoder(s).
## 📢 Announcements
- [ ] More robust user control over split model naming and other specifications
- [ ] Easy in-app uploading of models
- [ ] Fix potential memory leak
- [ ] Provide sample code for creating split models, converting text metrics outputs to LaTEX tables, etc.
- [ ] Eliminate UI hang due to MTLBox recreation, or add loading screen
- [ ] In-app splitting of MTL models
- [ ] Create requirements.txt
- [ ] Support devices without NPUs by searching for alternative processors
## 📏 Installation
- Option 1: Download Android Studio and build from this repository
- Option 2: Download the APK provided
## 🔧 Hardware Requirements
This app only works for Android devices with SDK>=26, though SDK=34+ is recommended. Some key functions, specifically photo picking and camera operation, require permission that can be activated once in-app via a pop up. It is also recommended for your device to have an NPU (preferable) or GPU.
## 💻 Usage
Before attempting to run the model, it is highly recommended that you peruse the settings and verify that everything is set up as you want it. Once all settings are to your liking, simply press the image icon in the bottom right corner of your screen.<br />
**PUT IMAGE HERE**<br />
To view the model input and output(s) along with recorded metrics, simply swipe to navigate different views. Responsiveness is dependent on model performance and postprocessing speeds.<br />
The settings menu can be accessed via the three dots in the top right hand corner of the home page. The navigation accessible via the top left corner should not be used at this time, and users should remain on the "Upload" page regardless of if they are uploading their input image or using the device's camera.<br />
**PUT IMAGE HERE**<br />
Within the settings, there are currently three menus: Metrics, Camera, and Model.<br />
**PUT IMAGE HERE**
### Metrics
The various metrics should be understandable purely from their name and units. Some metrics, including (but not limited to) memory usage, battery usage, power consumption, and temperature change, should only be considered over large sample sizes, where large is considered minimally 10 seconds.<br />
**PUT IMAGE HERE**
### Camera
The camera menu allows you to enable/disable the device's camera as well as other key settings, and therefore **should not be skipped regardless of whether you plan to use the camera or not.**<br />
Resolution refers to the *input resolution required by the model*. The default resolution of (224, 224) is common for most models.<br />
The metric limiters determine when metrics should be recorded, allowing for more accurate benchmarking in the areas described previously.<br />
**PUT IMAGE HERE**
### Model
Most settings here should be straightforward. **It is essential that all outputs are mapped to a postprocessing method, as the default method does not currently work for ONNX models.** These outputs, as far as I'm aware, are case sensitive and should match **exactly** with the output dictionary produced by the model in, say, a Python environment.<br />
**PUT IMAGE HERE**
## 🙌 Acknowledgements
Thank you to my advisor [Professor Sherief Reda](https://scale-lab.github.io/) for his guidance and expertise. Thank you to [Mahdi Boulila](https://github.com/MahdiBoulila) for your tireless mentorship his support. Thank you to [Harb Lin](https://engineering.brown.edu/people/shangran-lin) for his contributions to model-level acceleration.
## ✍️ Authorship
Current authors of the project:
- [Austin Funk](https://austin-funk.github.io/)

## 📖 Citation
If you find BeAR helpful in your research, please let us know!

## License
MIT License. See [LICENSE](LICENSE) file
