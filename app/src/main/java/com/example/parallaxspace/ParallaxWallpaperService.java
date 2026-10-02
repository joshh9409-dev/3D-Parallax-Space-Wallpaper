package com.example.parallaxspace;

import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

public class ParallaxWallpaperService extends WallpaperService {
    @Override public Engine onCreateEngine() {
        return new ParallaxEngine(this);
    }

    private static class ParallaxEngine extends WallpaperService.Engine {
        private final ParallaxRenderer renderer;
        private final ParallaxWallpaperService service;

        ParallaxEngine(ParallaxWallpaperService service) {
            this.service = service;
            renderer = new ParallaxRenderer(service);
        }

        @Override public void onVisibilityChanged(boolean visible) {
            renderer.setVisible(visible);
        }

        @Override public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            renderer.setSurface(holder, width, height);
        }

        @Override public void onSurfaceCreated(SurfaceHolder holder) {
            renderer.setSurface(holder, 1, 1);
        }

        @Override public void onSurfaceDestroyed(SurfaceHolder holder) {
            renderer.destroy();
        }

        @Override public void onOffsetsChanged(float xOffset, float yOffset,
                float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            renderer.setLauncherOffset(xOffset - 0.5f, yOffset - 0.5f);
        }
    }
}
