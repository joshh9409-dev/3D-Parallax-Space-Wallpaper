package com.example.parallaxspace;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class ParallaxRenderer implements SensorEventListener {
    private final Context context;
    private SurfaceHolder holder;
    private int width=1,height=1;
    private volatile boolean visible=true, running=false;
    private Thread thread;

    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private volatile float targetX=0,targetY=0;
    private float camX=0,camY=0;
    private float launcherX=0,launcherY=0;

    private android.opengl.EGLDisplay display;
    private android.opengl.EGLContext eglContext;
    private android.opengl.EGLSurface eglSurface;
    private int program;
    private int uResolution,uTime,uCam,uPage;
    private int positionLocation;
    private FloatBuffer vertexBuffer;

    public ParallaxRenderer(Context c){
        context=c;
        sensorManager=(SensorManager)c.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor=sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if(rotationSensor==null)
            rotationSensor=sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
    }

    public synchronized void setSurface(SurfaceHolder h,int w,int ht){
        holder=h; width=Math.max(1,w); height=Math.max(1,ht);
        if(visible) start();
    }

    public void setLauncherOffset(float x,float y){
        launcherX=Math.max(-0.5f,Math.min(0.5f,x));
        launcherY=Math.max(-0.5f,Math.min(0.5f,y));
    }

    public synchronized void setVisible(boolean v){
        visible=v;
        if(v) start(); else stop();
    }

    public synchronized void destroy(){ stop(); }

    private synchronized void start(){
        if(running || holder==null || holder.getSurface()==null) return;
        running=true;
        if(rotationSensor!=null)
            sensorManager.registerListener(this,rotationSensor,SensorManager.SENSOR_DELAY_GAME);
        thread=new Thread(this::renderLoop,"ParallaxWallpaperGL");
        thread.start();
    }

    private synchronized void stop(){
        running=false;
        if(sensorManager!=null) sensorManager.unregisterListener(this);
        if(thread!=null){
            try{thread.join(500);}catch(Exception ignored){}
            thread=null;
        }
    }

    private void renderLoop(){
        try{
            initEgl();
            long start=System.nanoTime();
            while(running && visible && holder!=null){
                float t=(System.nanoTime()-start)/1_000_000_000f;
                camX += (targetX+launcherX*0.35f-camX)*0.055f;
                camY += (targetY+launcherY*0.35f-camY)*0.055f;
                draw(t);
                EGL14.eglSwapBuffers(display,eglSurface);
                try{Thread.sleep(12);}catch(Exception ignored){}
            }
        } finally { releaseEgl(); }
    }

    private void initEgl(){
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] ver=new int[2];
        EGL14.eglInitialize(display,ver,0,ver,1);
        int[] cfg={
            EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,
            EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
            EGL14.EGL_NONE
        };
        android.opengl.EGLConfig[] configs=new android.opengl.EGLConfig[1];
        int[] n=new int[1];
        EGL14.eglChooseConfig(display,cfg,0,configs,0,1,n,0);
        int[] attrs={EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE};
        eglContext=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,attrs,0);
        eglSurface=EGL14.eglCreateWindowSurface(display,configs[0],holder.getSurface(),new int[]{EGL14.EGL_NONE},0);
        EGL14.eglMakeCurrent(display,eglSurface,eglSurface,eglContext);

        float[] vertices={-1f,-1f, 3f,-1f, -1f,3f};
        vertexBuffer=ByteBuffer.allocateDirect(vertices.length*4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.put(vertices).position(0);

        program=link(VERTEX,FRAGMENT);
        positionLocation=GLES20.glGetAttribLocation(program,"aPosition");
        if(positionLocation<0) throw new IllegalStateException("aPosition attribute missing");
        uResolution=GLES20.glGetUniformLocation(program,"uResolution");
        uTime=GLES20.glGetUniformLocation(program,"uTime");
        uCam=GLES20.glGetUniformLocation(program,"uCam");
        uPage=GLES20.glGetUniformLocation(program,"uPage");
    }

    private void draw(float time){
        GLES20.glViewport(0,0,width,height);
        GLES20.glUseProgram(program);
        GLES20.glUniform2f(uResolution,width,height);
        GLES20.glUniform1f(uTime,time);
        GLES20.glUniform2f(uCam,camX,camY);
        GLES20.glUniform2f(uPage,launcherX,launcherY);
        vertexBuffer.position(0);
        GLES20.glEnableVertexAttribArray(positionLocation);
        GLES20.glVertexAttribPointer(positionLocation,2,GLES20.GL_FLOAT,false,0,vertexBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,3);
    }

    private void releaseEgl(){
        if(display!=null){
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(eglSurface!=null) EGL14.eglDestroySurface(display,eglSurface);
            if(eglContext!=null) EGL14.eglDestroyContext(display,eglContext);
            EGL14.eglTerminate(display);
        }
        display=null; eglSurface=null; eglContext=null;
    }

    private int link(String vs,String fs){
        int v=GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(v,vs); GLES20.glCompileShader(v);
        int[] ok=new int[1];
        GLES20.glGetShaderiv(v,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0) throw new IllegalStateException("Vertex shader: "+GLES20.glGetShaderInfoLog(v));
        int f=GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(f,fs); GLES20.glCompileShader(f);
        GLES20.glGetShaderiv(f,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0) throw new IllegalStateException("Fragment shader: "+GLES20.glGetShaderInfoLog(f));
        int p=GLES20.glCreateProgram();
        GLES20.glAttachShader(p,v); GLES20.glAttachShader(p,f); GLES20.glLinkProgram(p);
        GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);
        if(ok[0]==0) throw new IllegalStateException("Program link: "+GLES20.glGetProgramInfoLog(p));
        GLES20.glDeleteShader(v); GLES20.glDeleteShader(f);
        return p;
    }

    @Override public void onSensorChanged(SensorEvent e){
        if(e.sensor.getType()!=Sensor.TYPE_ROTATION_VECTOR &&
           e.sensor.getType()!=Sensor.TYPE_GAME_ROTATION_VECTOR) return;
        float[] r=new float[9];
        SensorManager.getRotationMatrixFromVector(r,e.values);
        targetX=Math.max(-0.09f,Math.min(0.09f,-r[2]*0.10f));
        targetY=Math.max(-0.09f,Math.min(0.09f,r[5]*0.10f));
    }
    @Override public void onAccuracyChanged(Sensor s,int a){}

    private static final String VERTEX =
        "attribute vec2 aPosition; void main(){gl_Position=vec4(aPosition,0.0,1.0);}";

    /*
      A single real-time GPU scene. The scene deliberately contains depth-separated
      background, moons, ringed planet, ring particles and foreground rocks.
      Camera motion changes each layer by a different amount, producing the requested
      'background moving behind the foreground' parallax effect.
    */
    private static final String FRAGMENT =
        "precision highp float;\n"+
        "uniform vec2 uResolution,uCam,uPage; uniform float uTime;\n"+
        "#define PI 3.14159265359\n"+
        "float hash21(vec2 p){p=fract(p*vec2(123.34,456.21));p+=dot(p,p+45.32);return fract(p.x*p.y);}\n"+
        "float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);float a=hash21(i),b=hash21(i+vec2(1,0)),c=hash21(i+vec2(0,1)),d=hash21(i+vec2(1,1));return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);}\n"+
        "vec3 stars(vec2 p,float scale,float seed){vec2 q=floor(p*scale);float h=hash21(q+seed);float s=step(0.993,h);float b=pow(max(0.0,1.0-length(fract(p*scale)-0.5)*2.0),8.0);return vec3(s*b*(0.35+0.65*hash21(q+8.7)));}\n"+
        "float sphere(vec3 ro,vec3 rd,vec3 c,float r,out vec3 n){vec3 oc=ro-c;float b=dot(oc,rd),cc=dot(oc,oc)-r*r;float d=b*b-cc;if(d<0.0)return -1.0;float t=-b-sqrt(d);if(t<0.0)t=-b+sqrt(d);if(t<0.0)return -1.0;vec3 hp=ro+rd*t;n=normalize(hp-c);return t;}\n"+
        "void main(){\n"+
        " vec2 uv=(gl_FragCoord.xy-0.5*uResolution)/uResolution.y;\n"+
        " vec3 col=vec3(0.004,0.008,0.022);\n"+
        " vec2 bg=uv+uCam*0.20+uPage*0.10;\n"+
        " float neb=noise(bg*2.0+uTime*0.006)+0.5*noise(bg*5.0-uTime*0.003);\n"+
        " col+=vec3(0.015,0.045,0.12)*neb;\n"+
        " col+=stars(bg,18.0,2.0)+stars(bg+0.37,31.0,9.0)*0.7;\n"+
        " vec2 g=uv+uCam*0.45; float gal=exp(-pow((g.y+0.10*sin(g.x*2.0))*5.0,2.0));\n"+
        " col+=gal*vec3(0.035,0.025,0.07)*(0.4+0.6*noise(g*3.0));\n"+
        " vec3 ro=vec3((uCam+uPage)*-0.30,0.0); vec3 rd=normalize(vec3(uv,-1.85));\n"+
        " vec3 n; float t;\n"+
        " vec3 mc=vec3(-0.72+uCam.x*0.8,0.52+uCam.y*0.5,-0.35);\n"+
        " t=sphere(ro,rd,mc,0.14,n); if(t>0.0){float l=max(0.0,dot(n,normalize(vec3(-0.7,0.8,1.0))));col=mix(col,vec3(0.20,0.24,0.30)*(0.35+0.8*l),0.96);}\n"+
        " vec3 pc=vec3(0.16+uCam.x*1.65+uPage.x*0.25,-0.22+uCam.y*1.65+uPage.y*0.25,-0.92);\n"+
        " t=sphere(ro,rd,pc,0.72,n); if(t>0.0){vec3 hp=ro+rd*t;vec3 p=normalize(hp-pc);float lat=asin(p.y);float bands=0.5+0.5*sin(lat*31.0+0.7*sin(lat*9.0));float detail=noise(p.xz*11.0+uTime*0.015);float light=max(0.0,dot(n,normalize(vec3(-0.8,0.5,1.0))));vec3 base=mix(vec3(0.12,0.08,0.045),vec3(0.72,0.42,0.16),bands);base*=0.72+0.35*detail;col=base*(0.18+0.95*light);}\n"+
        " vec3 rp=ro+rd*((-0.92-ro.z)/rd.z); vec2 q=rp.xy-pc.xy; q.x*=1.0; float rr=length(q);float ringBand=smoothstep(0.76,0.79,rr)-smoothstep(1.02,1.06,rr);float gaps=0.55+0.45*sin(rr*120.0)+0.2*sin(rr*260.0);float ringMask=ringBand*clamp(gaps,0.15,1.0);float ringLight=0.35+0.65*max(0.0,dot(normalize(vec3(0.0,0.45,0.89)),normalize(vec3(-0.7,0.5,1.0))));col=mix(col,vec3(0.80,0.56,0.28)*ringLight,ringMask*0.95);\n"+
        " vec2 fg=uv+uCam*2.2+uPage*0.45; for(int i=0;i<24;i++){float fi=float(i);vec2 cell=floor(fg*3.2+fi*1.73);float h=hash21(cell);vec2 pos=(fract(vec2(h,hash21(cell+4.0)))-0.5)*2.0;pos+=vec2(sin(uTime*0.08+fi)*0.08,cos(uTime*0.06+fi)*0.05);float d=length(fg-pos);float size=0.008+0.018*hash21(cell+9.0);float rock=smoothstep(size,0.0,d);col+=rock*vec3(0.22,0.16,0.10)*(0.5+0.5*hash21(cell+12.0));}\n"+
        " float sun=exp(-18.0*length(uv-vec2(-0.72,0.25)));col+=vec3(1.0,0.43,0.10)*sun*0.8;\n"+
        " gl_FragColor=vec4(col,1.0);\n"+
        "}\n";
}
