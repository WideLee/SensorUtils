package sysu.mobile.limk.sensorutils;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.Locale;

import sysu.mobile.limk.library.sensorutils.CalibrationActivity;
import sysu.mobile.limk.library.sensorutils.SensorUtils;


public class MainActivity extends AppCompatActivity {

    Button mCalibrateButton;
    Button mResetButton;
    TextView mGyroTextView;
    TextView mCompassTextView;

    SensorUtils mSensorUtils;

    Handler mHandler;

    Runnable mUpdateSensorRunnable = new Runnable() {
        @Override
        public void run() {
            double gyroAngle = mSensorUtils.getAngle();
            if (Double.MAX_VALUE == gyroAngle) {
                mGyroTextView.setText(String.format(Locale.ENGLISH, "NaN"));
            } else {
                mGyroTextView.setText(String.format(Locale.ENGLISH, "%f", gyroAngle));
            }
            mCompassTextView.setText(String.format(Locale.ENGLISH, "%f", mSensorUtils.getCompassDirection()));
            mHandler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandler = new Handler();

        mCalibrateButton = (Button) findViewById(R.id.btn_calibration);
        mResetButton = (Button) findViewById(R.id.btn_reset);
        mGyroTextView = (TextView) findViewById(R.id.tv_gyroscope_value);
        mCompassTextView = (TextView) findViewById(R.id.tv_compasss_value);

        mSensorUtils = SensorUtils.getInstance(getApplicationContext());

        mCalibrateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CalibrationActivity.class);
                startActivity(intent);
            }
        });

        mResetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSensorUtils.reset();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorUtils.registerSensor();
        mSensorUtils.reset();
        mHandler.post(mUpdateSensorRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorUtils.unregisterSensor();
        mHandler.removeCallbacks(mUpdateSensorRunnable);
    }
}
