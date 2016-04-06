package com.eveningoutpost.dexdrip;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.TimePicker;
import android.widget.Toast;

import com.eveningoutpost.dexdrip.Models.UserError.Log;
import com.eveningoutpost.dexdrip.Models.Sensor;
import com.eveningoutpost.dexdrip.UtilityModels.CollectionServiceStarter;
import com.eveningoutpost.dexdrip.utils.ActivityWithMenu;

import java.util.Calendar;


public class StartNewSensor extends ActivityWithMenu {
    public static String menu_name = "Start Sensor";
    private Button button;
    private DatePicker dp;
    private TimePicker tp;
    private CheckBox linkPickers;
    
    private int last_hour;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(Sensor.isActive() == false) {
            setContentView(R.layout.activity_start_new_sensor);
            button = (Button)findViewById(R.id.startNewSensor);
            dp = (DatePicker)findViewById(R.id.datePicker);
            tp = (TimePicker)findViewById(R.id.timePicker);
            tp.setIs24HourView(DateFormat.is24HourFormat(this));
            tp.setSaveFromParentEnabled(false);
            tp.setSaveEnabled(true);
            addListenerOnButton();
            
            tp.setOnTimeChangedListener(new TimePicker.OnTimeChangedListener() {

                public void onTimeChanged(TimePicker arg0, int arg1, int arg2) {
                    Log.d("NEW SENSOR", "new time " + arg1  + " " + arg2);

                    if(arg1 == 23 && last_hour == 0) {
                        Log.d("NEW SENSOR", "decreading day");
                        addDays(-1);

                    }
                    if (arg1 == 0 && last_hour == 23) {
                        Log.d("NEW SENSOR", "increasing day");
                        addDays(1);
                    }
                    last_hour = arg1;

                }
            });

            last_hour = tp.getCurrentHour();
            
        } else {
            Intent intent = new Intent(this, StopSensor.class);
            startActivity(intent);
            finish();
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        prefs.edit().putBoolean("start_sensor_link_pickers", linkPickers.isChecked()).apply();
    }

    @Override
    public String getMenuName() {
        return menu_name;
    }

    public void addListenerOnButton() {

        button = (Button)findViewById(R.id.startNewSensor);
        linkPickers = (CheckBox)findViewById(R.id.startSensorLinkPickers);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        linkPickers.setChecked(prefs.getBoolean("start_sensor_link_pickers", false));

        button.setOnClickListener(new View.OnClickListener() {
          public void onClick(View v) {

              Calendar calendar = Calendar.getInstance();
              calendar.set(dp.getYear(), dp.getMonth(), dp.getDayOfMonth(),
              tp.getCurrentHour(), tp.getCurrentMinute(), 0);
              long startTime = calendar.getTime().getTime();

              Sensor.create(startTime);
              Log.d("NEW SENSOR", "Sensor started at " + startTime);

              Toast.makeText(getApplicationContext(), "NEW SENSOR STARTED", Toast.LENGTH_LONG).show();
              
              SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
              prefs.edit().putBoolean("start_sensor_link_pickers", linkPickers.isChecked()).apply();
              
              CollectionServiceStarter.newStart(getApplicationContext());
              Intent intent;
              if(prefs.getBoolean("store_sensor_location",true)) {
                  intent = new Intent(getApplicationContext(), NewSensorLocation.class);
              } else {
                  intent = new Intent(getApplicationContext(), Home.class);
              }

              startActivity(intent);
              finish();
          }

        });

    }
    
    void addDays(int numberOfDays) {
        
        if(!linkPickers.isChecked()) {
            return;
        }
        
        Calendar calendar = Calendar.getInstance();
        calendar.set(dp.getYear(), dp.getMonth(), dp.getDayOfMonth(),
        tp.getCurrentHour(), tp.getCurrentMinute(), 0);
        
        calendar.add(Calendar.DAY_OF_YEAR, numberOfDays);
        dp.updateDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
    }

}
