package com.eveningoutpost.dexdrip;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.eveningoutpost.dexdrip.Models.UserError.Log;
import com.eveningoutpost.dexdrip.Models.AlertType;
import com.eveningoutpost.dexdrip.Models.Sensor;
import com.eveningoutpost.dexdrip.Services.MissedReadingService;
import com.eveningoutpost.dexdrip.UtilityModels.CollectionServiceStarter;
import com.eveningoutpost.dexdrip.utils.ActivityWithMenu;

import java.util.Calendar;


public class MissedReadingActivity extends ActivityWithMenu {
    public static String menu_name = "Missed reading";
    private Context mContext;
    
    private CheckBox checkboxEnableAlert;
    private CheckBox checkboxAllDay;
    
    private LinearLayout layoutTimeBetween;
    private LinearLayout timeInstructions;
    private TextView viewTimeStart;
    private TextView viewTimeEnd;
    private TextView timeInstructionsStart;
    private TextView timeInstructionsEnd;
    private EditText bgMissedMinutes;
    
    private TextView viewAlertTime;
    private TextView vieSelectTime;
    
    
    private int startHour = 0;
    private int startMinute = 0;
    private int endHour = 23;
    private int endMinute = 59;
    private int missedMinutes = 59;
    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_missed_readings);
        mContext = this;
        
        viewTimeStart = (TextView) findViewById(R.id.missed_reading_time_start);
        viewTimeEnd = (TextView) findViewById(R.id.missed_reading_time_end);
        checkboxAllDay = (CheckBox) findViewById(R.id.missed_reading_all_day);
        checkboxEnableAlert = (CheckBox) findViewById(R.id.missed_reading_enable_alert);
        
        layoutTimeBetween = (LinearLayout) findViewById(R.id.missed_reading_time_between);
        timeInstructions = (LinearLayout) findViewById(R.id.missed_reading_instructions);
        timeInstructionsStart = (TextView) findViewById(R.id.missed_reading_instructions_start);
        timeInstructionsEnd = (TextView) findViewById(R.id.missed_reading_instructions_end);
        bgMissedMinutes = (EditText) findViewById(R.id.missed_reading_bg_minutes);
        viewAlertTime = (TextView) findViewById(R.id.missed_reading_text_alert_time);
        vieSelectTime = (TextView) findViewById(R.id.missed_reading_text_select_time);
        
        // Set the different controls
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        int startMinutes = prefs.getInt("missed_readings_start", 0);
        int endMinutes = prefs.getInt("missed_readings_end", 0);
        boolean enableAlert = prefs.getBoolean("bg_missed_alerts",false);
        boolean allDay = prefs.getBoolean("missed_readings_all_day",true);
        
        checkboxEnableAlert.setChecked(enableAlert);
        checkboxAllDay.setChecked(allDay);
        
        startHour = AlertType.time2Hours(startMinutes);
        startMinute = AlertType.time2Minutes(startMinutes);
        endHour = AlertType.time2Hours(endMinutes);
        endMinute = AlertType.time2Minutes(endMinutes);
        bgMissedMinutes.setText(prefs.getString("bg_missed_minutes", "30"));
        
        addListenerOnButtons();
        enableAllControls();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Context context = getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putInt("missed_readings_start", AlertType.toTime(startHour, startMinute)).apply();
        prefs.edit().putInt("missed_readings_end", AlertType.toTime(endHour, endMinute)).apply();
        prefs.edit().putString("bg_missed_minutes", bgMissedMinutes.getText().toString()).apply();

        prefs.edit().putBoolean("bg_missed_alerts", checkboxEnableAlert.isChecked()).apply();
        prefs.edit().putBoolean("missed_readings_all_day", checkboxAllDay.isChecked()).apply();
        
        context.startService(new Intent(context, MissedReadingService.class));
    }

    @Override
    public String getMenuName() {
        return menu_name;
    }
    
    void EnableControls(boolean enabled) {
        layoutTimeBetween.setEnabled(enabled);
        timeInstructions.setEnabled(enabled);
        checkboxAllDay.setEnabled(enabled);
        bgMissedMinutes.setEnabled(enabled);
        viewAlertTime.setEnabled(enabled);
        vieSelectTime.setEnabled(enabled);
    }
    
    void enableAllControls() {
        boolean enableAlert = checkboxEnableAlert.isChecked();
        if(!enableAlert) {
            EnableControls(false);
        } else {
            EnableControls(true);
        }
        boolean allDay = checkboxAllDay.isChecked();
        if(allDay) {
            layoutTimeBetween.setVisibility(View.GONE);
            timeInstructions.setVisibility(View.GONE);
        } else {
            setTimeRanges();
        }
    }
    
   
    
    
    public void addListenerOnButtons() {
        checkboxAllDay.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            //          @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                enableAllControls();
            }
        });
        
        checkboxEnableAlert.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            //          @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                enableAllControls();
            }
        });
        View.OnClickListener startTimeListener = new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                TimePickerDialog mTimePicker = new TimePickerDialog(mContext, AlertDialog.THEME_HOLO_DARK, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker timePicker, int selectedHour, int selectedMinute) {
                        startHour = selectedHour;
                        startMinute = selectedMinute;
                        setTimeRanges();
                    }
                }, startHour, startMinute, DateFormat.is24HourFormat(mContext));
                mTimePicker.setTitle("Select start time");
                mTimePicker.show();

            }
        } ;
        
        View.OnClickListener endTimeListener = new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                TimePickerDialog mTimePicker = new TimePickerDialog(mContext, AlertDialog.THEME_HOLO_DARK, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker timePicker, int selectedHour, int selectedMinute) {
                        endHour = selectedHour;
                        endMinute = selectedMinute;
                        setTimeRanges();
                    }
                }, endHour, endMinute, DateFormat.is24HourFormat(mContext));
                mTimePicker.setTitle("Select end time");
                mTimePicker.show();

            }
        };
        
        viewTimeStart.setOnClickListener(startTimeListener);
        timeInstructionsStart.setOnClickListener(startTimeListener);
        viewTimeEnd.setOnClickListener(endTimeListener);
        timeInstructionsEnd.setOnClickListener(endTimeListener);

    }
    
    public void setTimeRanges() {
        timeInstructions.setVisibility(View.VISIBLE);
        layoutTimeBetween.setVisibility(View.VISIBLE);
        viewTimeStart.setText(EditAlertActivity.timeFormatString(mContext,startHour, startMinute));
        viewTimeEnd.setText(EditAlertActivity.timeFormatString(mContext, endHour, endMinute));
    }

}
