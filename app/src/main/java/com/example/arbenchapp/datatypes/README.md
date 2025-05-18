# Datatypes
This folder contains all the different custom classes used for data storage, organized into pre- and postprocessing sections. Most of them 
should be self explanitory and/or not in need of changing. A few of note are discussed below.
## ConversionMethod
When adding to this Enum, make sure to search through the project for where one of the other `ConversionMethod` enums is used and add your 
custom one accordingly. This can be slightly tedious but shouldn't end up being more than a few lines of code. `ConversionUtil` is probably 
the right place to start.
## ImagePage and ImagePageAdapter
These are used for displaying images with captions in the UI. These likely won't need to be changed.
## Preferences
The different preference files are used for custom viewing and editing of specific settings. These likely won't need to be changed and should 
be helpful for implementing more unique settings (rather than a simple toggle).
