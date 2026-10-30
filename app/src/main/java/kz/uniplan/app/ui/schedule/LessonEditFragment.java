package kz.uniplan.app.ui.schedule;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import kz.uniplan.app.R;
import kz.uniplan.app.databinding.FragmentLessonEditBinding;
import kz.uniplan.app.ui.common.BaseFragment;
import kz.uniplan.app.ui.common.Ui;
import kz.uniplan.app.ui.tasks.CoreText;
import kz.uniplan.app.ui.tasks.StudyViewModel;

public class LessonEditFragment extends BaseFragment {
    private FragmentLessonEditBinding b;
    private StudyViewModel vm;
    @Override public View onCreateView(@NonNull LayoutInflater inflater,ViewGroup container,Bundle state){b=FragmentLessonEditBinding.inflate(inflater,container,false);return b.getRoot();}
    @Override public void onViewCreated(@NonNull View view,Bundle state){ super.onViewCreated(view,state);
        vm=new ViewModelProvider(this).get(StudyViewModel.class);
        ArrayAdapter<String> days=new ArrayAdapter<>(requireContext(),android.R.layout.simple_spinner_item,getResources().getStringArray(R.array.core_days));days.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);b.daySpinner.setAdapter(days);
        b.daySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View view,int position,long id){vm.set("day",position+1);}public void onNothingSelected(AdapterView<?> parent){}});
        Ui.watch(b.roomInput,text->vm.set("room",text));
        Ui.watch(b.numberInput,text->vm.set("lessonNumber",text));
        Ui.watch(b.typeInput,text->vm.set("lessonType",text));
        b.typeInput.setSimpleItems(getResources().getStringArray(R.array.lesson_types));
        b.typeInput.setOnClickListener(v->b.typeInput.showDropDown());
        Ui.watch(b.lessonTeacherInput,text->vm.set("lessonTeacher",text));
        Ui.watch(b.linkInput,text->{vm.set("meetingUrl",text);b.openLink.setVisibility(text.trim().isEmpty()?View.GONE:View.VISIBLE);});
        b.openLink.setOnClickListener(v->LessonLinks.open(requireContext(),vm.text("meetingUrl")));
        b.subjectButton.setOnClickListener(v->{Bundle args=new Bundle();args.putString("resultKey","lesson_subject");NavHostFragment.findNavController(this).navigate(R.id.subjectPickerFragment,args);});
        b.manageSubjects.setOnClickListener(v->Ui.go(this,R.id.subjectListFragment,0));
        getParentFragmentManager().setFragmentResultListener("lesson_subject",getViewLifecycleOwner(),(key,result)->{vm.set("subject",result.getLong("subjectId"));updateSubject();});
        b.startButton.setOnClickListener(v->pickTime("start"));b.endButton.setOnClickListener(v->pickTime("end"));
        b.saveButton.setOnClickListener(v->{
            String number=vm.text("lessonNumber").trim();
            if(!number.isEmpty()&&(!number.matches("[0-9]{1,2}")||Integer.parseInt(number)==0)){b.numberInput.setError(getString(R.string.lesson_invalid_number));return;}
            String link=vm.text("meetingUrl").trim();
            if(!link.isEmpty()&&!LessonLinks.valid(link)){b.linkInput.setError(getString(R.string.lesson_invalid_link));return;}
            if(vm.number("subject",0)==0){Toast.makeText(requireContext(),R.string.core_subject_required,Toast.LENGTH_SHORT).show();return;}
            if(vm.number("end",590)<=vm.number("start",540)){new MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.core_invalid_time).setPositiveButton(R.string.ok,null).show();return;}vm.saveLesson();
        });
        b.deleteButton.setOnClickListener(v->new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.core_delete_lesson).setMessage(R.string.confirm_delete).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.delete,(d,w)->vm.deleteLesson(vm.number("id",0))).show());
        vm.revision.observe(getViewLifecycleOwner(),revision->render());vm.subjects.observe(getViewLifecycleOwner(),items->updateSubject());
        vm.busy.observe(getViewLifecycleOwner(),busy->{b.progress.setVisibility(busy?View.VISIBLE:View.GONE);enableForm();});
        vm.errors.observe(getViewLifecycleOwner(),event->{String error=event.take();if(error!=null)message(error);});
        vm.saved.observe(getViewLifecycleOwner(),event->{if(event.take()!=null){Toast.makeText(requireContext(),R.string.saved,Toast.LENGTH_SHORT).show();NavHostFragment.findNavController(this).navigateUp();}});
        vm.removed.observe(getViewLifecycleOwner(),event->{if(event.take()!=null)NavHostFragment.findNavController(this).navigateUp();});
        vm.loadLesson(getArguments()==null?0:getArguments().getLong("id"));
    }
    private void render(){
        b.roomInput.setText(vm.text("room"));b.daySpinner.setSelection((int)vm.number("day",CoreText.today())-1);
        b.numberInput.setText(vm.text("lessonNumber"));b.typeInput.setText(vm.text("lessonType"),false);
        b.lessonTeacherInput.setText(vm.text("lessonTeacher"));b.linkInput.setText(vm.text("meetingUrl"));
        b.startButton.setText(CoreText.time(requireContext(),(int)vm.number("start",540)));b.endButton.setText(CoreText.time(requireContext(),(int)vm.number("end",590)));
        b.deleteButton.setVisibility(vm.number("id",0)==0?View.GONE:View.VISIBLE);enableForm();updateSubject();
    }
    private void enableForm() {
        boolean enabled=!vm.isBusy()&&vm.flag("initialized");
        b.saveButton.setEnabled(enabled);b.deleteButton.setEnabled(enabled);b.subjectButton.setEnabled(enabled);
        b.manageSubjects.setEnabled(enabled);b.daySpinner.setEnabled(enabled);b.startButton.setEnabled(enabled);
        b.endButton.setEnabled(enabled);b.roomInput.setEnabled(enabled);
        b.numberInput.setEnabled(enabled);b.typeInput.setEnabled(enabled);b.lessonTeacherInput.setEnabled(enabled);b.linkInput.setEnabled(enabled);b.openLink.setEnabled(enabled);
    }
    private void updateSubject(){if(b!=null)b.subjectButton.setText(vm.number("subject",0)==0?getString(R.string.select_subject):CoreText.subject(requireContext(),vm.subjects.getValue(),vm.number("subject",0)));}
    private void pickTime(String key){
        int minutes=(int)vm.number(key,"start".equals(key)?540:590);
        new TimePickerDialog(requireContext(),(picker,hour,minute)->{vm.set(key,hour*60+minute);if(b!=null){b.startButton.setText(CoreText.time(requireContext(),(int)vm.number("start",540)));b.endButton.setText(CoreText.time(requireContext(),(int)vm.number("end",590)));}},minutes/60,minutes%60,true).show();
    }
    @Override public void onDestroyView(){b=null;super.onDestroyView();}
}
