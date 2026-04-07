An android library based on FFMPEG which process audio file and provide an array of samples. Based on the processed data, you can easily draw custom waveform using the value of the processed data array as the height of the single column. Average processing time is equal to 1 second for audio with duration **3 min 20 seconds** and **1 hour** audio will be processed in approximately 20 seconds.

### Note

This library is a fork of original project of same name by [lincollincol](https://github.com/lincollincol), repurposed to work with [Felicity Music Player](https://github.com/Hamza417/Felicity), supports cancellation and other under the hood changed to make it more suitable for the app. This library offers no gaurantee of production use outside its original purpose to be used in [Felicity](https://github.com/Hamza417/Felicity), if you need to use this library then head to the original repo [Amplituda](https://github.com/lincollincol/Amplituda) instead.

### Download
``` groovy
allprojects {
  repositories {
    mavenCentral()
  }
}
```
``` groovy
dependencies {
  implementation 'io.github.hamza417:Amplituda:3.0.1'
}
```

### License

```
   Copyright 2020-present lincollincol

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
   
   FFmpeg
   -------
   Third-party components:
   This software uses libraries from the FFmpeg project under the LGPLv2.1. 
   FFmpeg is a separate project and is licensed under the LGPL. See https://ffmpeg.org for details.
   See FFmpeg License and Legal Considerations (https://www.ffmpeg.org/legal.html)
```
