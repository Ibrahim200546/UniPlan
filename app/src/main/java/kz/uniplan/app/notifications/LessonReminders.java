package kz.uniplan.app.notifications;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import kz.uniplan.app.R;
import kz.uniplan.app.UniPlanApp;
import kz.uniplan.app.data.auth.AuthRepository;
import kz.uniplan.app.data.local.AppDatabase;
import kz.uniplan.app.data.local.entity.LessonEntity;
import kz.uniplan.app.data.local.entity.SubjectEntity;
import kz.uniplan.app.domain.schedule.LessonTimes;
import kz.uniplan.app.ui.auth.AuthActivity;

/** Only the current account has alarms. One alarm is kept for the next undelivered lesson. */
public final class LessonReminders {
    public static final String FIRE="kz.uniplan.app.LESSON_REMINDER", CHANNEL="lessons";
    private final UniPlanApp app;
    private final AlarmManager alarms;
    private final NotificationManager notifications;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicInteger generation=new AtomicInteger();
    public LessonReminders(UniPlanApp app){
        this.app=app;alarms=(AlarmManager)app.getSystemService(Context.ALARM_SERVICE);notifications=(NotificationManager)app.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26){NotificationChannel channel=new NotificationChannel(CHANNEL,app.getString(R.string.reminder_channel),NotificationManager.IMPORTANCE_HIGH);notifications.createNotificationChannel(channel);}
        app.auth().session().observeForever(session->{notifications.cancelAll();refresh();});
        app.data().lessons().observeForever(lessons->refresh());
        app.data().subjects().observeForever(subjects->refresh());
    }
    private SharedPreferences prefs(String uid){return app.getSharedPreferences("reminders_"+uid,Context.MODE_PRIVATE);}
    public int minutes(){AuthRepository.Session session=app.auth().current();return session==null?0:prefs(session.uid).getInt("minutes",10);}
    public void setMinutes(int minutes){AuthRepository.Session session=app.auth().current();if(session==null)return;if(minutes!=0&&minutes!=5&&minutes!=10&&minutes!=15&&minutes!=30)throw new IllegalArgumentException("Unsupported lead time");prefs(session.uid).edit().putInt("minutes",minutes).apply();if(minutes==0)notifications.cancelAll();refresh();}
    public boolean exactAllowed(){return Build.VERSION.SDK_INT<31||alarms.canScheduleExactAlarms();}
    private PendingIntent alarmIntent(Intent intent){intent.setClass(app,LessonReminderReceiver.class).setAction(FIRE);return PendingIntent.getBroadcast(app,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public void refresh(){int ticket=generation.incrementAndGet();worker.execute(()->schedule(ticket));}
    private void schedule(int ticket){
        if(ticket!=generation.get())return;
        alarms.cancel(alarmIntent(new Intent()));
        AuthRepository.Session owner=app.auth().current();int lead=minutes();
        if(owner==null||lead==0||!NotificationManagerCompat.from(app).areNotificationsEnabled())return;
        long now=System.currentTimeMillis(),chosenStart=Long.MAX_VALUE;LessonEntity chosen=null;
        for(LessonEntity lesson:AppDatabase.get(app).studyDao().lessons(owner.uid)){
            long start=LessonTimes.nextStart(lesson.dayOfWeek,lesson.startMinute,now,ZoneId.systemDefault());
            if(prefs(owner.uid).getLong("sent_"+lesson.id,0)==start)start=LessonTimes.nextStart(lesson.dayOfWeek,lesson.startMinute,start,ZoneId.systemDefault());
            if(start<chosenStart){chosen=lesson;chosenStart=start;}
        }
        if(chosen==null||ticket!=generation.get()||owner!=app.auth().current())return;
        long fireAt=Math.max(now+1000,chosenStart-lead*60000L);
        PendingIntent pending=alarmIntent(new Intent().putExtra("owner",owner.uid).putExtra("lesson",chosen.id).putExtra("start",chosenStart));
        try {if(exactAllowed())alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,fireAt,pending);else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,fireAt,pending);}
        catch(SecurityException revoked){alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,fireAt,pending);}
    }
    public void receive(Intent intent,Runnable finished){
        worker.execute(()->{try{
            if(FIRE.equals(intent.getAction()))deliver(intent);
            schedule(generation.incrementAndGet());
        }finally{finished.run();}});
    }
    private void deliver(Intent intent){
        AuthRepository.Session owner=app.auth().current();long id=intent.getLongExtra("lesson",0),start=intent.getLongExtra("start",0),now=System.currentTimeMillis();
        if(owner==null||!owner.uid.equals(intent.getStringExtra("owner"))||minutes()==0||start<=now||start-now>minutes()*60000L+2000)return;
        LessonEntity lesson=AppDatabase.get(app).studyDao().lesson(owner.uid,id);
        if(lesson==null||LessonTimes.nextStart(lesson.dayOfWeek,lesson.startMinute,now,ZoneId.systemDefault())!=start||prefs(owner.uid).getLong("sent_"+id,0)==start)return;
        SubjectEntity subject=AppDatabase.get(app).studyDao().subject(owner.uid,lesson.subjectId);if(subject==null)return;
        if(!NotificationManagerCompat.from(app).areNotificationsEnabled()||app.auth().current()!=owner)return;
        Intent open=new Intent(app,AuthActivity.class).putExtra("openSchedule",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(app,(int)id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String time=String.format(java.util.Locale.getDefault(),"%02d:%02d",lesson.startMinute/60,lesson.startMinute%60);
        String body=time+(lesson.room.isEmpty()?"":" · "+lesson.room);
        NotificationCompat.Builder builder=new NotificationCompat.Builder(app,CHANNEL).setSmallIcon(R.drawable.ic_schedule).setContentTitle(subject.name)
                .setContentText(body).setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).setContentIntent(content);
        try{notifications.notify("lesson_"+owner.uid+"_"+id,0,builder.build());prefs(owner.uid).edit().putLong("sent_"+id,start).apply();}catch(SecurityException ignored){/* Permission can be revoked between check and notify. */}
    }
}
