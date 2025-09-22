package com.poc.lab.batch.deputy;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class ImageByteUtils {

    private static final String PNG = "png";

    private ImageByteUtils() {
    }

    public static byte[] png(BufferedImage img) throws IOException {
        try (var bos = new java.io.ByteArrayOutputStream(16 * 1024)) {
            ImageIO.write(img, PNG, bos);
            return bos.toByteArray();
        }
    }
}
