Kernel Flasher
====

This repo is for NC2 on SM-N900A. Various ports exist - check your phone's xda forum.

Porting
----
Modify the options in `app/src/main/java/com/benwaffle/nc2flasher/MainActivity.java`.

Compile with `./gradlew assembleRelease`, the output will be `./app/build/outputs/apk/app-release-unsigned.apk`.

How it Works
----
All this does is run `dd if=kernel.img of=/dev/block/platform/msm_sdcc.1/by-name/boot`
