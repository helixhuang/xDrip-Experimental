package com.eveningoutpost.dexdrip;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.eveningoutpost.dexdrip.Models.UserError.Log;
import com.eveningoutpost.dexdrip.Services.MissedReadingService;

import com.eveningoutpost.dexdrip.UtilityModels.CollectionServiceStarter;

/**
 * Created by stephenblack on 11/3/14.
 */
public class AutoStart extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("DexDrip", "Service auto starter, starting!");
        CollectionServiceStarter.newStart(context);
        context.startService(new Intent(context, MissedReadingService.class));
    }
}
