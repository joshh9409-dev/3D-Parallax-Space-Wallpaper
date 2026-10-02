# 3D Parallax Space Live Wallpaper

This is an Android live-wallpaper project designed around the effect from the reference clip:
different depth layers respond differently when the phone moves, making the foreground object feel separated from the distant space background.

The project is intentionally set up for an Android/GitHub Actions workflow.

## Build
Open in Android Studio, or push to GitHub and run the included workflow.

## Intended visual architecture
- Deep starfield / nebula: far depth, very small parallax
- Distant moons: medium-far depth
- Main ringed planet: foreground depth
- Ring particles and asteroids: very near depth
- Rotation-vector sensor: smooth camera/parallax movement
- Launcher offsets: additional home-screen page parallax

## Important
The current Java renderer contains the service, sensor handling and project scaffolding. The final GLES renderer should be completed with a dedicated EGL thread/surface so it renders directly to the wallpaper surface instead of using Canvas.
