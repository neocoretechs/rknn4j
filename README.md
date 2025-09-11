<h2>RKNN4j Java Bindings for RockChip NPU Object Segmentation</h2>
Currently supports the following models:
* InceptionSSD_v2
* YOLOv5s
* YOLOv11s
JDK support level is at 25. Tested on Rock5B and Odroid M2 with RK3588.

<h4>RockChip Neural Processing Unit for Java</h4><p/>
Java bindings to the RockChip RK3588 Neural Processing Unit.<br/>
The RK3588 is a 6 TOPS NPU integrated into the RK3588 SoC and available on a number of SBC's (Single Board Computers).

The NPU is primarily oriented toward object recognition and semantic segmentation using a number of pretrained models.
For recognizing multiple common objects from realtime video streams such as a webcam, the YOLOv5s (You Only Look Once version 5, small) and
InceptionSSDv2 (Single Shot Detect version 2) standard model are supported and the .rknn files that are loaded
into the NPU to affect inference are included in the release.

Other files in the release include:
RGA (RockChip Graphics Accelerator) library for fast image resizing
OpenCV2 library
RKNN Runtime NPU runtime library
These files are included as .so shared object files that the Java VM loads at runtime.

NOTE: THESE FILES <b>MUST</b> BE INSTALLED IN THE /usr/lib/jni DIRECTORY!

If you are using the YOLOv5s model, you will have to run your program as root because the RGA requires rootaccess to use the low level
harware features of the RockChip Graphics Accelerator.

The com.neocoretechs.rknn4j.runtime.Model main method contains all the calls to the NPU necessary to load a model, perform inference and
get results back from a static JPEG image:

```
java com.neocoretechs.rknn4j.runtime.Model <model_file> <image jpeg file> <inception | yolo>
```
The code assumes that the support files are installed in the /etc/model directory. These files are included with the release and include:
/etc/model/coco_labels_list.txt
/etc/model/box_priors.txt
For InceptionSSD
/etc/model/coco_80_labels_list.txt
For YOLOv5s

The "coco" files contain the names of the 80 object categories in the COCO dataset (Common Objects In Context)
upon which the object recognition is based.

