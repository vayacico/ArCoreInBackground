package com.vayacico.arcoreinbackground;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;

import org.zeromq.ZMQ;

import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class ArCoreService extends Service  implements GLSurfaceView.Renderer{

    boolean running = true;

    Session mSession = null;
    private WindowManager windowManager;
    private View view;
    org.zeromq.ZMQ.Context zeromqContext;
    org.zeromq.ZMQ.Socket socket;
    String ip_address;
    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();

    //サービスに接続するためのBinder
    public class MyServiceLocalBinder extends Binder {
        //サービスの取得
        ArCoreService getService() {
            return ArCoreService.this;
        }
    }

    private final IBinder mBinder = new MyServiceLocalBinder();

    Thread thread;

    @Override
    public void onCreate() {

        Log.d("ArCoreService","onCreate:"+android.os.Process.myPid());

        //For Running test
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running){
                    try {
                        Log.d("Thread","running:"+android.os.Process.myPid());
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        thread.start();

        //オーバーレイの設定
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        int typeLayer = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        windowManager = (WindowManager)getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams (
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                typeLayer,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);

        int dpScale = (int)getResources().getDisplayMetrics().density;
        params.gravity=  Gravity.TOP | Gravity.END;
        params.x = 20 * dpScale; // 20dp
        params.y = 80 * dpScale; // 80dp

        //ビューの追加
        final ViewGroup nullParent = null;
        view = layoutInflater.inflate(R.layout.arcore_servce,nullParent);
        windowManager.addView(view,params);

        //レンダラーの設定
        GLSurfaceView surfaceView = view.findViewById(R.id.surfaceView_float);
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.d("ArCoreService","onStartCommand");

        Notification notification = new Notification.Builder(this)
                .setContentTitle("AA")
                .setContentText("AA")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        startForeground(1,notification);

        ip_address = intent.getStringExtra("ip_address");

        return START_STICKY;
    }

    @Override
    public void onDestroy() {

        Log.d("ArCoreService","onDestroy");

        running = false;
        windowManager.removeView(view);
        if(mSession!=null) {
            mSession.pause();
            mSession = null;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {

        Log.d("ArCoreService","onBind:"+android.os.Process.myPid());

        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {

        Log.d("ArCoreService","onUnbind");

        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {

        Log.d("ArCoreService","onRebind");

        super.onRebind(intent);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {

        Log.d("ArCoreService","onSurfaceCreated");

        try {
            backgroundRenderer.createOnGlThread(/*context=*/ this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {

    }

    @Override
    public void onDrawFrame(GL10 gl) {

        if(mSession==null || backgroundRenderer.getTextureId()==-1){
            return;
        }

        try {
            //フレームデータの取得
            mSession.setCameraTextureName(backgroundRenderer.getTextureId());
            Frame frame = mSession.update();

            //フレームからポジショントラッキングデータの取り出し
            Pose pose = frame.getCamera().getDisplayOrientedPose();
            float[] axis = frame.getCamera().getPose().getZAxis();

            //Proto化
            ARCoreProto.Position position = ARCoreProto.Position.newBuilder().setTx(pose.tx()).setTy(pose.ty()).setTz(pose.tz()).build();
            ARCoreProto.Rotation rotation = ARCoreProto.Rotation.newBuilder().setQw(pose.qw()).setQx(pose.qx()).setQy(pose.qy()).setQz(pose.qz()).build();
            ARCoreProto.ZAxis zAxis = ARCoreProto.ZAxis.newBuilder().setX(axis[0]).setY(axis[1]).setZ(axis[2]).build();
            ARCoreProto.ArCoreMessage message = ARCoreProto.ArCoreMessage.newBuilder().setPosition(position).setRotation(rotation).setZaxis(zAxis).build();

            Log.d("onDrawFrame",message.toString());

        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }


    }


    boolean startArCoreSession(){

        if(mSession==null) {
            try {
                mSession = new Session(this);
                mSession.resume();
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    void stopArCoreSession(){
        if(mSession==null)return;
        mSession.pause();
        mSession=null;
    }

    boolean connectToPC(){

        zeromqContext = org.zeromq.ZMQ.context(1);
        socket = zeromqContext.socket(ZMQ.REQ);
        Log.d("ip_address",ip_address);
        socket.connect("tcp://"+ip_address);

        return true;
    }

}
