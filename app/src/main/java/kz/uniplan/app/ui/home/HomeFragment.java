package kz.uniplan.app.ui.home;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import kz.uniplan.app.R;
import kz.uniplan.app.data.local.entity.*;
import kz.uniplan.app.databinding.FragmentHomeBinding;
import kz.uniplan.app.domain.model.StudyItem;
import kz.uniplan.app.ui.common.BaseFragment;
import kz.uniplan.app.ui.common.Ui;
import kz.uniplan.app.ui.schedule.LessonAdapter;
import kz.uniplan.app.ui.tasks.CoreText;
import kz.uniplan.app.ui.tasks.StudyViewModel;
import kz.uniplan.app.ui.tasks.TaskAdapter;

public class HomeFragment extends BaseFragment implements QuickActionsFragment.Callback {
    private FragmentHomeBinding b;
    private StudyViewModel vm;
    private TaskAdapter tasksAdapter;
    private LessonAdapter lessonsAdapter;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable ticker=new Runnable(){@Override public void run(){if(b!=null){render();handler.postDelayed(this,60000);}}};
    @Override public View onCreateView(@NonNull LayoutInflater inflater,ViewGroup container,Bundle state){b=FragmentHomeBinding.inflate(inflater,container,false);return b.getRoot();}
    @Override public void onViewCreated(@NonNull View view,Bundle state){ super.onViewCreated(view,state);
        vm=new ViewModelProvider(this).get(StudyViewModel.class);
        if(getChildFragmentManager().findFragmentById(R.id.quickActions)==null)getChildFragmentManager().beginTransaction().replace(R.id.quickActions,new QuickActionsFragment()).commit();
        tasksAdapter=new TaskAdapter(new TaskAdapter.Actions(){public void open(long id){Ui.go(HomeFragment.this,R.id.taskDetailFragment,id);}public void complete(long id,boolean complete){vm.complete(id,complete);}public void delete(long id){}},true);
        lessonsAdapter=new LessonAdapter(id->Ui.go(this,R.id.lessonEditFragment,id),true);
        b.tasksList.setLayoutManager(new LinearLayoutManager(requireContext()));b.tasksList.setAdapter(tasksAdapter);
        b.lessonsList.setLayoutManager(new LinearLayoutManager(requireContext()));b.lessonsList.setAdapter(lessonsAdapter);
        vm.tasks.observe(getViewLifecycleOwner(),items->render());vm.subjects.observe(getViewLifecycleOwner(),items->render());vm.lessons.observe(getViewLifecycleOwner(),items->render());vm.profile.observe(getViewLifecycleOwner(),profile->render());
        vm.errors.observe(getViewLifecycleOwner(),event->{String error=event.take();if(error!=null)message(error);});
    }
    private void render(){
        if(b==null||tasksAdapter==null)return;
        long now=System.currentTimeMillis();b.dateText.setText(DateFormat.getDateInstance(DateFormat.FULL,getResources().getConfiguration().getLocales().get(0)).format(new Date(now)));
        ProfileEntity profile=vm.profile.getValue();String name=profile==null||profile.displayName==null?"":profile.displayName;
        if(name.isEmpty()&&app().auth().current()!=null)name=app().auth().current().displayName;
        b.welcomeText.setText(getString(R.string.core_welcome,name));
        String group=profile==null?"":profile.studyGroup;
        b.groupText.setText(group);b.groupText.setVisibility(group.isEmpty()?View.GONE:View.VISIBLE);
        int active=0,done=0,overdue=0;List<TaskEntity> tasks=new ArrayList<>();
        if(vm.tasks.getValue()!=null)for(TaskEntity task:vm.tasks.getValue()){if(task.completed)done++;else{active++;tasks.add(task);if(task.dueAt<now)overdue++;}}
        b.activeText.setText(getString(R.string.core_active_count,active));b.doneText.setText(getString(R.string.core_done_count,done));b.overdueText.setText(getString(R.string.core_overdue_count,overdue));
        tasks.sort(StudyItem.BY_DUE);if(tasks.size()>3)tasks=new ArrayList<>(tasks.subList(0,3));tasksAdapter.submit(tasks,vm.subjects.getValue());
        b.emptyTasks.setVisibility(tasks.isEmpty()?View.VISIBLE:View.GONE);
        List<LessonEntity> lessons=vm.lessons.getValue()==null?new ArrayList<>():new ArrayList<>(vm.lessons.getValue());
        lessons.sort(Comparator.comparingLong(l->nextOccurrence(l,now)));if(lessons.size()>3)lessons=new ArrayList<>(lessons.subList(0,3));lessonsAdapter.submit(lessons,vm.subjects.getValue());
        b.emptyLessons.setVisibility(lessons.isEmpty()?View.VISIBLE:View.GONE);
        b.emptyText.setText(vm.tasks.getValue()==null?R.string.loading:R.string.core_home_empty);
        b.emptyText.setVisibility(vm.tasks.getValue()==null||((vm.subjects.getValue()==null||vm.subjects.getValue().isEmpty())&&active+done==0)?View.VISIBLE:View.GONE);
    }
    private long nextOccurrence(LessonEntity lesson,long now){
        Calendar start=Calendar.getInstance();start.setTimeInMillis(now);start.set(Calendar.HOUR_OF_DAY,0);start.set(Calendar.MINUTE,0);start.set(Calendar.SECOND,0);start.set(Calendar.MILLISECOND,0);
        int delta=(lesson.dayOfWeek-CoreText.today()+7)%7;
        Calendar end=(Calendar)start.clone();end.set(Calendar.HOUR_OF_DAY,lesson.endMinute/60);end.set(Calendar.MINUTE,lesson.endMinute%60);
        if(delta==0&&end.getTimeInMillis()<=now)delta=7;
        start.add(Calendar.DAY_OF_YEAR,delta);start.set(Calendar.HOUR_OF_DAY,lesson.startMinute/60);start.set(Calendar.MINUTE,lesson.startMinute%60);return start.getTimeInMillis();
    }
    @Override public void onCreateTask(){Ui.go(this,R.id.action_home_to_task,0);}
    @Override public void onCreateNote(){Ui.go(this,R.id.action_home_to_note,0);}
    @Override public void onResume(){super.onResume();handler.post(ticker);}
    @Override public void onPause(){handler.removeCallbacks(ticker);super.onPause();}
    @Override public void onDestroyView(){handler.removeCallbacks(ticker);b.tasksList.setAdapter(null);b.lessonsList.setAdapter(null);tasksAdapter=null;lessonsAdapter=null;b=null;super.onDestroyView();}
}
