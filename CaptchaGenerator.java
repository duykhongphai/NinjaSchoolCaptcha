package com.nsoz.captcha;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class CaptchaGenerator {
    private static final int BASE_WIDTH = 180;
    private static final int BASE_HEIGHT = 35;
    private static final int ARROW_SIZE = 16;
    private static final int CIRCLE_SIZE = 24;
    private static final int ARROW_COUNT = 6;
    private static final int ARROW_SPACING = 2;
    private static final Color BACKGROUND_COLOR = new Color(250, 250, 250);
    private static final Color[] BRIGHT_COLORS = {
            Color.RED, Color.BLUE, Color.GREEN, Color.MAGENTA, Color.CYAN, Color.ORANGE,
            new Color(255, 105, 180), new Color(128, 0, 128), new Color(255, 165, 0)
    };

    private static final String NOISE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";

    public enum Direction {
        UP(1), LEFT(0), RIGHT(2);

        private final int code;

        Direction(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    private static class CaptchaContext {
        final String arrowSequence;
        final int zoomLevel;
        final int width;
        final int height;

        CaptchaContext(String arrowSequence, int zoomLevel, int width, int height) {
            this.arrowSequence = arrowSequence;
            this.zoomLevel = zoomLevel;
            this.width = width;
            this.height = height;
        }
    }

    public static CaptchaResult createCaptchaImage(int zoomLevel) {
        if (zoomLevel < 1 || zoomLevel > 4) {
            throw new IllegalArgumentException("Zoom level must be between 1 and 4");
        }
        int width = BASE_WIDTH * zoomLevel;
        int height = BASE_HEIGHT * zoomLevel;
        BufferedImage image = null;
        Graphics2D g2d = null;
        try {
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            g2d = image.createGraphics();
            setupRenderingHints(g2d);
            CaptchaContext context = generateCaptchaContext(zoomLevel, width, height);
            drawBackground(g2d, width, height, zoomLevel);
            drawNoiseCharacters(g2d, width, height, zoomLevel);
            drawArrowCircles(g2d, context);
            drawNoiseLines(g2d, width, height, zoomLevel);
            drawDistortionEffects(g2d, width, height, zoomLevel);
            image = applyImageEffects(image, zoomLevel);
            byte[] imageBytes = compressImage(image);
            return new CaptchaResult(imageBytes, context.arrowSequence);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate CAPTCHA", e);
        } finally {
            if (g2d != null) {
                g2d.dispose();
            }
        }
    }

    private static void setupRenderingHints(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    }

    private static CaptchaContext generateCaptchaContext(int zoomLevel, int width, int height) {
        Random rand = ThreadLocalRandom.current();
        Direction[] directions = Direction.values();
        StringBuilder sequence = new StringBuilder();
        for (int i = 0; i < ARROW_COUNT; i++) {
            Direction direction = directions[rand.nextInt(directions.length)];
            sequence.append(direction.getCode());
        }
        return new CaptchaContext(sequence.toString(), zoomLevel, width, height);
    }

    private static void drawBackground(Graphics2D g2d, int width, int height, int zoomLevel) {
        Random rand = ThreadLocalRandom.current();
        g2d.setColor(BACKGROUND_COLOR);
        g2d.fillRect(0, 0, width, height);
        for (int i = 0; i < width * height / 50; i++) {
            int x = rand.nextInt(width);
            int y = rand.nextInt(height);
            int brightness = 220 + rand.nextInt(35);
            g2d.setColor(new Color(brightness, brightness, brightness));
            g2d.fillRect(x, y, zoomLevel, zoomLevel);
        }
    }

    private static void drawArrowCircles(Graphics2D g2d, CaptchaContext context) {
        String sequence = context.arrowSequence;
        int zoomLevel = context.zoomLevel;
        int width = context.width;
        int height = context.height;
        int centerY = height / 2;
        int scaledCircleSize = CIRCLE_SIZE * zoomLevel;
        int scaledSpacing = ARROW_SPACING * zoomLevel;
        int totalArrowWidth = ARROW_COUNT * scaledCircleSize + (ARROW_COUNT - 1) * scaledSpacing;
        int startX = (width - totalArrowWidth) / 2 + scaledCircleSize / 2;
        for (int i = 0; i < ARROW_COUNT; i++) {
            int centerX = startX + i * (scaledCircleSize + scaledSpacing);
            drawCircleBackground(g2d, centerX, centerY, scaledCircleSize, zoomLevel);
            int directionCode = Character.getNumericValue(sequence.charAt(i));
            Direction direction = getDirectionByCode(directionCode);
            drawArrow(g2d, centerX, centerY, direction, zoomLevel);
        }
    }

    private static Direction getDirectionByCode(int code) {
        for (Direction dir : Direction.values()) {
            if (dir.getCode() == code) {
                return dir;
            }
        }
        return Direction.UP;
    }

    private static void drawCircleBackground(Graphics2D g2d, int centerX, int centerY, int size, int zoomLevel) {
        Random rand = ThreadLocalRandom.current();
        Color shadowColor = new Color(180 + rand.nextInt(50), 180 + rand.nextInt(50), 180 + rand.nextInt(50), 60);
        g2d.setColor(shadowColor);
        g2d.fillOval(centerX - size / 2 + zoomLevel / 2, centerY - size / 2 + zoomLevel / 2, size, size);
        Color color1 = new Color(230 + rand.nextInt(25), 230 + rand.nextInt(25), 230 + rand.nextInt(25));
        Color color2 = new Color(200 + rand.nextInt(55), 200 + rand.nextInt(55), 200 + rand.nextInt(55));

        GradientPaint gradient = new GradientPaint(
                centerX - size / 2f, centerY - size / 2f, color1,
                centerX + size / 2f, centerY + size / 2f, color2
        );
        g2d.setPaint(gradient);
        g2d.fillOval(centerX - size / 2, centerY - size / 2, size, size);
        Color borderColor = new Color(150 + rand.nextInt(80), 150 + rand.nextInt(80), 150 + rand.nextInt(80));
        g2d.setColor(borderColor);
        g2d.setStroke(new BasicStroke(Math.max(1, zoomLevel / 2)));
        g2d.drawOval(centerX - size / 2, centerY - size / 2, size, size);
    }

    private static void drawArrow(Graphics2D g2d, int centerX, int centerY, Direction direction, int zoomLevel) {
        Random rand = ThreadLocalRandom.current();
        int scaledArrowSize = ARROW_SIZE * zoomLevel;
        Color arrowColor = BRIGHT_COLORS[rand.nextInt(BRIGHT_COLORS.length)];
        g2d.setColor(arrowColor);
        g2d.setStroke(new BasicStroke(Math.max(1, zoomLevel / 2)));
        int[] xPoints, yPoints;
        switch (direction) {
            case UP:
                xPoints = new int[]{centerX, centerX - scaledArrowSize / 2, centerX - scaledArrowSize / 4,
                        centerX - scaledArrowSize / 4, centerX + scaledArrowSize / 4,
                        centerX + scaledArrowSize / 4, centerX + scaledArrowSize / 2};
                yPoints = new int[]{centerY - scaledArrowSize / 3, centerY + scaledArrowSize / 6,
                        centerY + scaledArrowSize / 6, centerY + scaledArrowSize / 3,
                        centerY + scaledArrowSize / 3, centerY + scaledArrowSize / 6,
                        centerY + scaledArrowSize / 6};
                break;

            case LEFT:
                xPoints = new int[]{centerX - scaledArrowSize / 3, centerX + scaledArrowSize / 6,
                        centerX + scaledArrowSize / 6, centerX + scaledArrowSize / 3,
                        centerX + scaledArrowSize / 3, centerX + scaledArrowSize / 6,
                        centerX + scaledArrowSize / 6};
                yPoints = new int[]{centerY, centerY - scaledArrowSize / 2, centerY - scaledArrowSize / 4,
                        centerY - scaledArrowSize / 4, centerY + scaledArrowSize / 4,
                        centerY + scaledArrowSize / 4, centerY + scaledArrowSize / 2};
                break;
            case RIGHT:
                xPoints = new int[]{centerX + scaledArrowSize / 3, centerX - scaledArrowSize / 6,
                        centerX - scaledArrowSize / 6, centerX - scaledArrowSize / 3,
                        centerX - scaledArrowSize / 3, centerX - scaledArrowSize / 6,
                        centerX - scaledArrowSize / 6};
                yPoints = new int[]{centerY, centerY - scaledArrowSize / 2, centerY - scaledArrowSize / 4,
                        centerY - scaledArrowSize / 4, centerY + scaledArrowSize / 4,
                        centerY + scaledArrowSize / 4, centerY + scaledArrowSize / 2};
                break;

            default:
                return;
        }

        g2d.fillPolygon(xPoints, yPoints, xPoints.length);
        g2d.setColor(arrowColor.brighter());
        g2d.drawPolygon(xPoints, yPoints, xPoints.length);
    }

    private static void drawNoiseCharacters(Graphics2D g2d, int width, int height, int zoomLevel) {
        Random rand = ThreadLocalRandom.current();
        Font font = new Font("Arial", Font.BOLD, 12 * zoomLevel);
        g2d.setFont(font);
        g2d.setColor(Color.LIGHT_GRAY);
        FontMetrics fm = g2d.getFontMetrics();
        int fontHeight = fm.getHeight();
        int numRows = (height / fontHeight) + 1;
        for (int row = 0; row < numRows; row++) {
            int y = (row * fontHeight) + fm.getAscent();
            int currentX = 0;
            while (currentX < width) {
                char noiseChar = NOISE_CHARS.charAt(rand.nextInt(NOISE_CHARS.length()));

                Graphics2D g2dCopy = (Graphics2D) g2d.create();
                try {
                    int rotation = rand.nextInt(31) - 15;
                    g2dCopy.rotate(Math.toRadians(rotation), currentX, y);

                    Composite originalComposite = g2dCopy.getComposite();
                    g2dCopy.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
                    g2dCopy.drawString(String.valueOf(noiseChar), currentX, y);
                    g2dCopy.setComposite(originalComposite);
                } finally {
                    g2dCopy.dispose();
                }

                int charWidth = fm.stringWidth(String.valueOf(noiseChar));
                currentX += Math.max(charWidth / 2, fm.charWidth('M') / 3);
            }
        }
    }

    private static void drawNoiseLines(Graphics2D g2d, int width, int height, int zoomLevel) {
        Random rand = ThreadLocalRandom.current();

        Color[] boldColors = {Color.RED, Color.BLACK, Color.WHITE, Color.BLUE, Color.GREEN,
                new Color(128, 0, 128), new Color(255, 165, 0)};
        int numLines = (3 + zoomLevel) + rand.nextInt(2 + zoomLevel);
        for (int i = 0; i < numLines; i++) {
            Color lineColor = boldColors[rand.nextInt(boldColors.length)];
            g2d.setColor(lineColor);

            int lineThickness = 1 + (zoomLevel > 2 ? 1 : 0);
            g2d.setStroke(new BasicStroke(lineThickness));

            int lineType = rand.nextInt(4);

            switch (lineType) {
                case 0:
                    int y = rand.nextInt(height);
                    g2d.drawLine(0, y, width, y);
                    break;
                case 1:
                    int x = rand.nextInt(width);
                    g2d.drawLine(x, 0, x, height);
                    break;
                case 2:
                    int startY = rand.nextInt(height);
                    int endY = rand.nextInt(height);
                    g2d.drawLine(0, startY, width, endY);
                    break;
                case 3:
                    int startY2 = rand.nextInt(height);
                    int endY2 = rand.nextInt(height);
                    g2d.drawLine(width, startY2, 0, endY2);
                    break;
            }
        }
    }

    private static void drawDistortionEffects(Graphics2D g2d, int width, int height, int zoomLevel) {
        Random rand = ThreadLocalRandom.current();
        g2d.setStroke(new BasicStroke(0.8f * zoomLevel));
        for (int i = 0; i < 3; i++) {
            Color wavyColor = new Color(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256), 80);
            g2d.setColor(wavyColor);

            int startY = rand.nextInt(height);
            int amplitude = (5 + rand.nextInt(10)) * zoomLevel;
            int frequency = (20 + rand.nextInt(20)) * zoomLevel;

            for (int x = 0; x < width - 1; x++) {
                int y1 = startY + (int) (amplitude * Math.sin(2 * Math.PI * x / frequency));
                int y2 = startY + (int) (amplitude * Math.sin(2 * Math.PI * (x + 1) / frequency));
                g2d.drawLine(x, y1, x + 1, y2);
            }
        }
        for (int i = 0; i < 30 * zoomLevel; i++) {
            Color dotColor = new Color(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256),
                    60 + rand.nextInt(100));
            g2d.setColor(dotColor);

            int x = rand.nextInt(width);
            int y = rand.nextInt(height);
            int size = (1 + rand.nextInt(3)) * zoomLevel;

            g2d.fillOval(x, y, size, size);
        }
    }

    private static BufferedImage applyImageEffects(BufferedImage image, int zoomLevel) {
        if (zoomLevel == 1) return image;
        if (zoomLevel > 1) {
            image = applyPixelatedEffect(image, zoomLevel);
        }
        if (zoomLevel > 2) {
            image = applyBlurEffect(image, zoomLevel);
        }

        return image;
    }

    private static BufferedImage applyPixelatedEffect(BufferedImage image, int zoomLevel) {
        int width = image.getWidth();
        int height = image.getHeight();
        int pixelSize = Math.max(1, zoomLevel / 4);

        BufferedImage pixelated = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = pixelated.createGraphics();

        for (int x = 0; x < width; x += pixelSize) {
            for (int y = 0; y < height; y += pixelSize) {
                int rgb = image.getRGB(x, y);
                Color color = new Color(rgb);
                g2d.setColor(color);
                g2d.fillRect(x, y, pixelSize, pixelSize);
            }
        }

        g2d.dispose();
        return pixelated;
    }

    private static BufferedImage applyBlurEffect(BufferedImage image, int zoomLevel) {
        float blurStrength = 0.3f + (zoomLevel * 0.1f);
        int blurSize = Math.max(1, zoomLevel / 6);

        if (blurSize > 1) {
            float[] blurKernel = new float[blurSize * blurSize];
            Arrays.fill(blurKernel, blurStrength / blurKernel.length);
            Kernel kernel = new Kernel(blurSize, blurSize, blurKernel);
            ConvolveOp convolve = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
            return convolve.filter(image, null);
        }

        return image;
    }

    private static byte[] compressImage(BufferedImage image) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageWriter writer = null;
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
            if (!writers.hasNext()) {
                throw new RuntimeException("No PNG writer found");
            }
            writer = writers.next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.8f);
            }
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
            return baos.toByteArray();

        } finally {
            if (writer != null) {
                writer.dispose();
            }
        }
    }
}