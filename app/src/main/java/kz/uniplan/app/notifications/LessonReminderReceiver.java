package kz.uniplan.app.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import kz.uniplan.app.UniPlanApp;

public final class LessonReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        PendingResult pending=goAsync();UniPlanApp.get(context).reminders().receive(intent,pending::finish);
    }
}
