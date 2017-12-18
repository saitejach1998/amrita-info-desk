package com.njlabs.amrita.aid;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.njlabs.amrita.aid.landing.Landing;

public class SplashScreen extends AppCompatActivity {
SharedPreferences preferences;
Boolean FirstTime;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);

        preferences = getSharedPreferences("FirstTime",MODE_PRIVATE);
        FirstTime = preferences.getBoolean("FirstTime",true);


        if(FirstTime) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {

                    SharedPreferences.Editor editor = preferences.edit();
                    FirstTime = false;
                    editor.putBoolean("FirstTime", FirstTime);
                    editor.apply();
                    Intent i = new Intent(SplashScreen.this, Landing.class);
                    startActivity(i);
                    finish();
                }
            }, 2000);
        }
        else{
            Intent i = new Intent(SplashScreen.this, Landing.class);
            startActivity(i);
            overridePendingTransition(0,0);
            finish();




        }

    }


}
