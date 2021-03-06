package com.vayacico.arcoreinbackground;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {

    static Integer OVERLAY_PERMISSION_REQ_CODE = 1234;
    ArCoreService arCoreService;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Button startBtn = findViewById(R.id.start_btn);
        final Button stopBtn  = findViewById(R.id.stop_btn);
        Button debugBtn = findViewById(R.id.debug_btn);
        Button debugBtn2 = findViewById(R.id.debug_btn2);
        Button debugBtn3 = findViewById(R.id.debug_btn3);
        Button debugBtn4 = findViewById(R.id.debug_btn4);

        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //オーバーレイの許可を取得
                if(!Settings.canDrawOverlays(getApplication())){
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()));
                    startActivityForResult(intent,OVERLAY_PERMISSION_REQ_CODE);
                }
                else {
                    //サービスの開始
                    EditText editText = findViewById(R.id.edit_text);
                    Intent intent = new Intent(getApplication(), ArCoreService.class);
                    intent.putExtra("ip_address",editText.getText().toString());
                    startForegroundService(intent);

                }
            }
        });

        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //サービスのストップ
                Intent intent = new Intent(getApplication(), ArCoreService.class);
                stopService(intent);
            }
        });

        debugBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplication(), ArCoreService.class);
                bindService(intent,mConnection, Context.BIND_AUTO_CREATE);
            }
        });

        debugBtn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                unbindService(mConnection);
            }
        });

        debugBtn3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(arCoreService==null)return;

                boolean result = arCoreService.startArCoreSession();

                if(result){
                    Log.d("startArCoreSession","Success");
                }else{
                    Log.d("startArCoreSession","Failed");
                }
            }
        });

        debugBtn4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(arCoreService!=null){
                    arCoreService.stopArCoreSession();
                }
            }
        });
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d("MainActivity","onServiceConnected");

            arCoreService =  ((ArCoreService.MyServiceLocalBinder)service).getService();

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d("MainActivity","onServiceDisconnected");
        }
    };
}
