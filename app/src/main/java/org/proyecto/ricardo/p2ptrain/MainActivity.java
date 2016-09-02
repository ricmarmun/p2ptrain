package org.proyecto.ricardo.p2ptrain;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.RotateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends Activity {
    private static final float ROTATE_FROM = 0.0f;
    private static final float ROTATE_TO = -10.0f * 360.0f;
    public String nick;
    private Animation shake;
    private TextView tv;
    private EditText nickname;

    public void onCreate(Bundle savedInstanceState) {

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        shake = AnimationUtils.loadAnimation(this, R.anim.animationshake);
        tv = (TextView) findViewById(R.id.alertNick);
        nickname = (EditText) findViewById(R.id.Nick);
    }

    public void clickIcon(View view){

        nick = nickname.getText().toString();
        if(!nick.isEmpty() && nick.length() >= 3) {
            ImageView favicon = (ImageView) findViewById(R.id.mainIcon);

            RotateAnimation r;
            r = new RotateAnimation(ROTATE_FROM, ROTATE_TO, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            r.setDuration((long) 2 * 1500);
            r.setRepeatCount(0);
            favicon.startAnimation(r);

            MediaPlayer player = MediaPlayer.create(this, R.raw.introsound);
            player.start();

            tv.setVisibility(View.GONE);
            nickname.setFocusable(false);

            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);

            new Timer().schedule(new TimerTask() {
                public void run() {
                    startActivity(new Intent(MainActivity.this, TabWidget.class));
                    finish();
                }
            }, 3000);
        }else{
            tv.setVisibility(View.VISIBLE);
            tv.startAnimation(shake);
        }
    }
}
