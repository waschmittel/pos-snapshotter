package de.flubba;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Owns the webcam: device discovery, the frame-grab loop, and device switching.
 * Frames are pushed to {@code onFrame}; errors to {@code onError}. All grabber
 * access is serialized on an internal lock so switching devices cannot race the
 * grab loop.
 */
@Slf4j
public final class Camera implements AutoCloseable {

    private static final int FRAME_WIDTH = 768;
    private static final int FRAME_HEIGHT = 512;

    private final Object lock = new Object();
    private FrameGrabber grabber;
    private final Java2DFrameConverter converter = new Java2DFrameConverter();
    private final Consumer<BufferedImage> onFrame;
    private final Consumer<String> onError;

    public Camera(int initialDevice,
                  AtomicBoolean running,
                  BooleanSupplier active,
                  Consumer<BufferedImage> onFrame,
                  Consumer<String> onError) {
        this.onFrame = onFrame;
        this.onError = onError;
        synchronized (lock) {
            grabber = open(initialDevice);
        }
        PollingLoop.start("camera-loop", running, active, this::grabFrame);
    }

    public Dimension frameSize() {
        synchronized (lock) {
            return grabber != null
                    ? new Dimension(grabber.getImageWidth(), grabber.getImageHeight())
                    : new Dimension(FRAME_WIDTH, FRAME_HEIGHT);
        }
    }

    public void select(int deviceIndex) {
        Thread.ofPlatform().name("camera-switch").start(() -> {
            synchronized (lock) {
                closeGrabber();
                grabber = open(deviceIndex);
            }
        });
    }

    @Override
    public void close() {
        synchronized (lock) {
            closeGrabber();
        }
    }

    private void grabFrame() throws Exception {
        BufferedImage image;
        synchronized (lock) {
            if (grabber == null) return;
            Frame frame = grabber.grab();
            if (frame == null) return;
            // convert while holding the lock: the Frame references grabber-owned
            // native buffers that select()/close() would free
            image = converter.convert(frame);
        }
        if (image != null) {
            onFrame.accept(image);
        }
    }

    private FrameGrabber open(int deviceIndex) {
        try {
            var g = new OpenCVFrameGrabber(deviceIndex);
            g.setImageWidth(FRAME_WIDTH);
            g.setImageHeight(FRAME_HEIGHT);
            g.start();
            log.info("Camera {} started: {}x{}", deviceIndex, g.getImageWidth(), g.getImageHeight());
            return g;
        } catch (FrameGrabber.Exception e) {
            log.error("Failed to start camera {}", deviceIndex, e);
            onError.accept("Failed to start camera " + deviceIndex + ": " + e.getMessage());
            return null;
        }
    }

    private void closeGrabber() {
        try {
            if (grabber != null) {
                grabber.stop();
                grabber.release();
                grabber = null;
            }
        } catch (FrameGrabber.Exception e) {
            log.error("Error stopping webcam", e);
        }
    }

    public static String[] detectCameraNames() {
        try {
            var process = new ProcessBuilder("system_profiler", "SPCameraDataType", "-json")
                    .redirectErrorStream(true).start();
            var output = new String(process.getInputStream().readAllBytes());
            process.waitFor();

            var names = new ArrayList<String>();
            int searchFrom = 0;
            while (true) {
                int nameKeyPos = output.indexOf("\"_name\"", searchFrom);
                if (nameKeyPos < 0) break;
                int colonPos = output.indexOf(":", nameKeyPos);
                int quoteStart = output.indexOf("\"", colonPos + 1);
                int quoteEnd = output.indexOf("\"", quoteStart + 1);
                if (quoteStart >= 0 && quoteEnd > quoteStart) {
                    names.add(output.substring(quoteStart + 1, quoteEnd));
                }
                searchFrom = quoteEnd + 1;
            }

            if (!names.isEmpty()) {
                return names.toArray(String[]::new);
            }
        } catch (Exception e) {
            log.warn("Could not detect camera names", e);
        }
        return new String[]{"Camera 0", "Camera 1", "Camera 2", "Camera 3"};
    }
}
