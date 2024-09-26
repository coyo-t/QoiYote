# QoiYote
A ReAlly REALLY small QOI file reader for Java

Said "Coy-Yo-Tee". Like How The Actual Word Is >:[

Requires whatever Java version added the FFM. 20 or 21 I think, idr.
I reccomend you just drop the package dirs straight into your source tree and modify it to your needs. I have it set up so it digests the QOI data into
an AWT `BufferedImage`, because I figured thats the linga franca of image data everyone using Java has.
As a result, getting the channel & colur space info is a little wonky and Probably Not Thread Safe but whatever.

Currently, theres only reading of QOI images from a `ByteBuffer` or `byte[]` (the latter just gets
shoved into a call to `ByteBuffer.wrap(...)`). Like I said, Modify It To Your Whims.

There's technically nothing stopping me from adding a QOI encoder (enQOIder?) but i just Dont Really Feel Like It.

Have Fun.

![birthdayyote2](https://github.com/user-attachments/assets/7024fe7c-8373-486c-9f45-35bdc14d941c)
