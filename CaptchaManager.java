package com.nsoz.captcha;

import com.nsoz.model.Char;

import java.util.concurrent.ConcurrentHashMap;

public class CaptchaManager {
    private static volatile CaptchaManager instance;
    private static final Object lock = new Object();
    private final ConcurrentHashMap<Integer, CaptchaSession> activeCaptchas;

    private static final int DEFAULT_MAX_FAILURES = 10;

    private static class CaptchaSession {
        final CaptchaResult captchaResult;
        final int sessionId;

        CaptchaSession(int sessionId, CaptchaResult captchaResult) {
            this.sessionId = sessionId;
            this.captchaResult = captchaResult;
        }

        void dispose() {
            if (captchaResult != null && !captchaResult.isDisposed()) {
                captchaResult.dispose();
            }
        }
    }

    private CaptchaManager() {
        this.activeCaptchas = new ConcurrentHashMap<>();
    }

    public static CaptchaManager getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new CaptchaManager();
                }
            }
        }
        return instance;
    }

    public void generateCaptcha(int sessionId, int zoomLevel) {
        if (zoomLevel < 1 || zoomLevel > 4) {
            throw new IllegalArgumentException("Zoom level must be between 1 and 4");
        }

        try {
            removeSessionAndCleanup(sessionId);
            CaptchaResult captchaResult = CaptchaGenerator.createCaptchaImage(zoomLevel);
            CaptchaSession session = new CaptchaSession(sessionId, captchaResult);
            activeCaptchas.put(sessionId, session);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate CAPTCHA for session " + sessionId, e);
        }
    }


    public void generateCaptchaForPlayer(Char player) {
        try {
            int sessionId = player.user.id;
            int zoomLevel = player.user.session.zoomLevel;
            generateCaptcha(sessionId, zoomLevel);
            player.captcha = 1;
            player.zone.getService().addItemCaptcha(player);
            player.getService().loadInfo();
        } catch (Exception ignored) {
        }
    }

    public boolean containsCaptcha(int sessionId) {
        CaptchaSession session = activeCaptchas.get(sessionId);
        if (session == null) {
            return false;
        }
        return !session.captchaResult.isDisposed();
    }

    public CaptchaResult getCaptcha(int sessionId) {
        CaptchaSession session = activeCaptchas.get(sessionId);
        if (session == null) {
            return null;
        }
        return session.captchaResult.isDisposed() ? null : session.captchaResult;
    }

    public void handlePlayerCaptchaInput(Char player, byte input) {
        int sessionId = player.user.id;
        CaptchaSession session = activeCaptchas.get(sessionId);
        if (session == null) {
            return;
        }
        CaptchaResult captchaResult = session.captchaResult;
        if (captchaResult.isDisposed()) {
            return;
        }
        boolean completed = captchaResult.addInput(input);
        if (completed) {
            removeCaptcha(sessionId);
            player.captcha = 0;
            player.getService().loadInfo();
            player.zone.getService().removeItem((short)-1);
        } else if (captchaResult.getEnteredValue().length() == 6) {
            if (captchaResult.getFailCount() >= DEFAULT_MAX_FAILURES) {
                captchaResult.captchaFailCount = 0;
                generateCaptchaForPlayer(player);
            } else {
                captchaResult.captchaFailCount++;
            }
        }
    }

    public void removeCaptcha(int sessionId) {
        CaptchaSession session = activeCaptchas.get(sessionId);
        if (session != null) {
            removeSessionAndCleanup(sessionId, session);
        }
    }

    private void removeSessionAndCleanup(int sessionId, CaptchaSession session) {
        activeCaptchas.remove(sessionId);
        if (session != null) {
            session.dispose();
        }
    }

    private void removeSessionAndCleanup(int sessionId) {
        try {
            CaptchaSession session = activeCaptchas.remove(sessionId);
            if (session != null) {
                session.dispose();
            }
        } catch (Exception e) {
            // Log but don't throw
        }
    }
}