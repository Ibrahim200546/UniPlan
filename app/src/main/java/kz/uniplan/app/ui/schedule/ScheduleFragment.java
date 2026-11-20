package kz.uniplan.app.ui.schedule;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import kz.uniplan.app.R;
import kz.uniplan.app.data.local.entity.LessonEntity;
import kz.uniplan.app.databinding.FragmentScheduleBinding;
import kz.uniplan.app.ui.common.BaseFragment;
import kz.uniplan.app.ui.common.Ui;
import kz.uniplan.app.ui.tasks.CoreText;
import kz.uniplan.app.ui.tasks.StudyViewModel;

public class ScheduleFragment extends BaseFragment {
    private FragmentScheduleBinding b;
    private StudyViewModel vm;
    private LessonAdapter adapter;
    private final androidx.activity.result.ActivityResultLauncher<String> notificationPermission=registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),granted->{
                if(granted)requestExactTime();else if(isAdded())android.widget.Toast.makeText(requireContext(),R.string.reminder_permission,android.widget.Toast.LENGTH_LONG).show();
            });
    @Override public View onCreateView(@NonNull LayoutInflater inflater,ViewGroup container,Bundle state){b=FragmentScheduleBinding.inflate(inflater,container,false);return b.getRoot();}
    @Override public void onViewCreated(@NonNull View view,Bundle state){ super.onViewCreated(view,state);
        vm=new ViewModelProvider(this).get(StudyViewModel.class);
        b.easyAdd.setOnClickListener(v->Ui.go(this,R.id.scheduleImportFragment,0));
        b.reminders.setOnClickListener(v->showReminders());
        String[] days=getResources().getStringArray(R.array.core_days);
        b.todayText.setText(getString(R.string.core_today_day,days[CoreText.today()-1]));
        ArrayAdapter<String> daysAdapter=new ArrayAdapter<>(requireContext(),android.R.layout.simple_spinner_item,days);daysAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);b.daySpinner.setAdapter(daysAdapter);
        b.daySpinner.setSelection((int)vm.number("day",CoreText.today())-1);
        b.daySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View view,int position,long id){vm.set("day",position+1);render();}public void onNothingSelected(AdapterView<?> parent){}});
        b.addButton.setOnClickListener(v->{Bundle args=new Bundle();args.putLong("id",0);args.putInt("day",(int)vm.number("day",CoreText.today()));NavHostFragment.findNavController(this).navigate(R.id.lessonEditFragment,args);});
        adapter=new LessonAdapter(id->Ui.go(this,R.id.lessonEditFragment,id),false);b.list.setLayoutManager(new LinearLayoutManager(requireContext()));b.list.setAdapter(adapter);
        vm.lessons.observe(getViewLifecycleOwner(),items->{render();if(items!=null&&!items.isEmpty())offerReminders();});vm.subjects.observe(getViewLifecycleOwner(),items->render());
    }
    private void offerReminders(){
        android.content.SharedPreferences preferences=requireContext().getSharedPreferences("reminder_ui",android.content.Context.MODE_PRIVATE);
        if(preferences.getBoolean("requested",false))return;
        preferences.edit().putBoolean("requested",true).apply();
        if(android.os.Build.VERSION.SDK_INT>=33&&androidx.core.content.ContextCompat.checkSelfPermission(requireContext(),android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        else requestExactTime();
    }
    private void showReminders(){
        int[] minutes={0,5,10,15,30};String[] labels=new String[minutes.length];int selected=0;
        kz.uniplan.app.notifications.LessonReminders reminders=kz.uniplan.app.UniPlanApp.get(requireContext()).reminders();
        for(int i=0;i<minutes.length;i++){labels[i]=minutes[i]==0?getString(R.string.reminder_off):getString(R.string.reminder_minutes,minutes[i]);if(minutes[i]==reminders.minutes())selected=i;}
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.reminder_title)
                .setSingleChoiceItems(labels,selected,(dialog,which)->{reminders.setMinutes(minutes[which]);dialog.dismiss();
                    android.widget.Toast.makeText(requireContext(),minutes[which]==0?getString(R.string.reminder_disabled):getString(R.string.reminder_enabled,minutes[which]),android.widget.Toast.LENGTH_SHORT).show();
                    if(minutes[which]>0){
                        if(android.os.Build.VERSION.SDK_INT>=33&&androidx.core.content.ContextCompat.checkSelfPermission(requireContext(),android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
                        else if(!androidx.core.app.NotificationManagerCompat.from(requireContext()).areNotificationsEnabled())
                            startActivity(new android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,requireContext().getPackageName()));
                        else requestExactTime();
                    }
                }).setNegativeButton(R.string.cancel,null).show();
    }
    private void requestExactTime(){
        if(!isAdded())return;
        kz.uniplan.app.notifications.LessonReminders reminders=kz.uniplan.app.UniPlanApp.get(requireContext()).reminders();reminders.refresh();
        if(android.os.Build.VERSION.SDK_INT>=31&&!reminders.exactAllowed())new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reminder_exact_title).setMessage(R.string.reminder_exact_help)
                .setPositiveButton(R.string.reminder_settings,(dialog,which)->{try{startActivity(new android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:"+requireContext().getPackageName())));}catch(android.content.ActivityNotFoundException ignored){}})
                .setNegativeButton(R.string.cancel,null).show();
    }
    private void render(){
        if(b==null||adapter==null)return;List<LessonEntity> visible=new ArrayList<>();
        if(vm.lessons.getValue()!=null)for(LessonEntity lesson:vm.lessons.getValue())if(lesson.dayOfWeek==vm.number("day",CoreText.today()))visible.add(lesson);
        visible.sort(Comparator.comparingInt((LessonEntity l)->l.startMinute).thenComparingLong(l->l.id));adapter.submit(visible,vm.subjects.getValue());
        b.countText.setText(getString(R.string.lesson_day_count,visible.size(),vm.lessons.getValue()==null?0:vm.lessons.getValue().size()));
        b.emptyText.setText(vm.lessons.getValue()==null?R.string.loading:R.string.core_lessons_empty);b.emptyText.setVisibility(visible.isEmpty()?View.VISIBLE:View.GONE);
    }
    @Override public void onResume(){super.onResume();kz.uniplan.app.UniPlanApp.get(requireContext()).reminders().refresh();if(b!=null)b.todayText.setText(getString(R.string.core_today_day,getResources().getStringArray(R.array.core_days)[CoreText.today()-1]));}
    @Override public void onDestroyView(){b.list.setAdapter(null);adapter=null;b=null;super.onDestroyView();}
}
