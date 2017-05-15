package sysu.mobile.limk.library.sensorutils;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class CalibrationActivity extends Activity {

    private Button mBeginButton;

    private SensorManager mSensorManager;
    private Sensor mGyroSensor;
    private double[] drift;
    private boolean isBeginCalibrate;
    private Activity activity;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        drift = new double[]{0, 0, 0, 0};
        isBeginCalibrate = false;
        activity = this;
        mBeginButton = (Button) findViewById(R.id.begin_calibrate_button);
        mBeginButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setEnabled(false);
                isBeginCalibrate = true;
                new CountDownTimer(20000, 1000) {

                    @Override
                    public void onTick(long millisUntilFinished) {
                        mBeginButton.setText("Waiting " + millisUntilFinished / 1000 + "s ...");
                    }

                    @Override
                    public void onFinish() {

                        isBeginCalibrate = false;

                        PreferenceUtils.saveDoubleValue(activity, "x_drift", drift[0]);
                        PreferenceUtils.saveDoubleValue(activity, "y_drift", drift[1]);
                        PreferenceUtils.saveDoubleValue(activity, "z_drift", drift[2]);
                        Log.i("drift", "x_drift: " + drift[0]);
                        Log.i("drift", "x_drift: " + drift[1]);
                        Log.i("drift", "x_drift: " + drift[2]);
                        mBeginButton.setText(getString(R.string.begin_calibrate));
                        mBeginButton.setEnabled(true);
                    }
                }.start();
            }
        });
    }

    private SensorEventListener mCalibrateGyroLogListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (isBeginCalibrate) {
                float axisX = event.values[0];
                float axisY = event.values[1];
                float axisZ = event.values[2];

                drift[0] = (axisX + drift[0] * drift[3]) / (drift[3] + 1);
                drift[1] = (axisY + drift[1] * drift[3]) / (drift[3] + 1);
                drift[2] = (axisZ + drift[2] * drift[3]) / (drift[3] + 1);

                drift[3]++;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.d("CalibrateGyroscope", accuracy + "");
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        mSensorManager.registerListener(mCalibrateGyroLogListener, mGyroSensor,
                SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    public void onStop() {
        mSensorManager.unregisterListener(mCalibrateGyroLogListener);
        super.onStop();
    }
}
