package com.nsoz.captcha;

import java.util.concurrent.atomic.AtomicBoolean;

public class CaptchaResult implements AutoCloseable {

    private byte[] imageBytes;
    private final String correctSequence;
    public final StringBuilder captchaEntered;
    public int captchaFailCount;
    private final Object inputLock = new Object();
    private final Object resourceLock = new Object();
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private static final int MAX_INPUT_LENGTH = 6;

    public CaptchaResult(byte[] imageBytes, String correctSequence) {
        this.imageBytes = imageBytes != null ? imageBytes.clone() : null;
        this.correctSequence = correctSequence;
        this.captchaEntered = new StringBuilder();
        this.captchaFailCount = 0;
    }

    public byte[] getImageBytes() {
        checkNotDisposed();
        synchronized (resourceLock) {
            return imageBytes != null ? imageBytes.clone() : new byte[0];
        }
    }

    public int getFailCount() {
        synchronized (inputLock) {
            return captchaFailCount;
        }
    }

    public String getEnteredValue() {
        synchronized (inputLock) {
            return captchaEntered.toString();
        }
    }

    public boolean addInput(byte input) {
        if (isDisposed()) {
            return false;
        }

        synchronized (inputLock) {
            if (input < 0 || input > 2) {
                return false;
            }
            if (captchaEntered.length() >= MAX_INPUT_LENGTH) {
                captchaEntered.deleteCharAt(0);
            }
            captchaEntered.append(input);
            boolean completed = captchaEntered.length() == MAX_INPUT_LENGTH && verify();
            if (completed) {
                dispose();
            }
            return completed;
        }
    }

    public boolean verify() {
        synchronized (inputLock) {
            return captchaEntered != null
                    && captchaEntered.toString().equals(correctSequence);
        }
    }

    public void dispose() {
        if (disposed.compareAndSet(false, true)) {
            synchronized (resourceLock) {
                imageBytes = null;
            }
            synchronized (inputLock) {
                captchaEntered.setLength(0);
            }
        }
    }

    @Override
    public void close() {
        dispose();
    }

    public boolean isDisposed() {
        return disposed.get();
    }

    private void checkNotDisposed() {
        if (isDisposed()) {
            throw new IllegalStateException("CaptchaResult has been disposed");
        }
    }
}