// =================================================================================
// 文件: app/src/main/java/com/yidroid/argesture/YuvToRgbConverter.java
// 描述: YUV到RGB的转换类。
// =================================================================================
package com.yidroid.argesture;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.media.Image;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;

import java.nio.ByteBuffer;

public class YuvToRgbConverter {

    private final RenderScript rs;
    private final ScriptIntrinsicYuvToRGB scriptYuvToRgb;
    private Allocation yuvAllocation;
    private Allocation rgbAllocation;
    private Bitmap outputBitmap;

    public YuvToRgbConverter(Context context) {
        rs = RenderScript.create(context);
        scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
    }

    public synchronized Bitmap yuvToRgb(Image image) {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Invalid image format");
        }

        int width = image.getWidth();
        int height = image.getHeight();

        if (outputBitmap == null || outputBitmap.getWidth() != width || outputBitmap.getHeight() != height) {
            outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            rgbAllocation = Allocation.createFromBitmap(rs, outputBitmap);
            Type.Builder yuvType = new Type.Builder(rs, Element.U8(rs)).setX(width * height * 3 / 2);
            yuvAllocation = Allocation.createTyped(rs, yuvType.create());
        }

        yuvAllocation.copyFrom(imageToByteArray(image));
        scriptYuvToRgb.setInput(yuvAllocation);
        scriptYuvToRgb.forEach(rgbAllocation);
        rgbAllocation.copyTo(outputBitmap);

        return outputBitmap;
    }

    private byte[] imageToByteArray(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();
        byte[] data = new byte[yBuffer.remaining() + uBuffer.remaining() + vBuffer.remaining()];
        yBuffer.get(data, 0, yBuffer.remaining());
        vBuffer.get(data, yBuffer.capacity(), vBuffer.remaining());
        uBuffer.get(data, yBuffer.capacity() + vBuffer.capacity(), uBuffer.remaining());
        return data;
    }
}
