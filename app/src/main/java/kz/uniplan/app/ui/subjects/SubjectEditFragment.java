package kz.uniplan.app.ui.subjects;

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
import kz.uniplan.app.databinding.FragmentSubjectEditBinding;
import kz.uniplan.app.ui.common.BaseFragment;
import kz.uniplan.app.ui.common.Ui;
import kz.uniplan.app.ui.tasks.StudyViewModel;
import kz.uniplan.app.util.AppearancePalette;

public class SubjectEditFragment extends BaseFragment {
    private FragmentSubjectEditBinding b;
    private StudyViewModel vm;
    private static final int[] COLORS={0xFF52796F,0xFF5278A7,0xFFB47A2E,0xFF8A64A9};
    @Override public View onCreateView(@NonNull LayoutInflater inflater,ViewGroup container,Bundle state){b=FragmentSubjectEditBinding.inflate(inflater,container,false);return b.getRoot();}
    @Override public void onViewCreated(@NonNull View view,Bundle state){ super.onViewCreated(view,state);
        vm=new ViewModelProvider(this).get(StudyViewModel.class);
        String[] colors={getString(R.string.core_green),getString(R.string.core_blue),getString(R.string.core_amber),getString(R.string.core_purple),getString(R.string.subject_color_custom)};
        String[] icons={getString(R.string.core_book),getString(R.string.core_checklist),getString(R.string.core_calendar)};
        ArrayAdapter<String> ca=new ArrayAdapter<>(requireContext(),android.R.layout.simple_spinner_item,colors);ca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);b.colorSpinner.setAdapter(ca);
        ArrayAdapter<String> ia=new ArrayAdapter<>(requireContext(),android.R.layout.simple_spinner_item,icons);ia.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);b.iconSpinner.setAdapter(ia);
        Ui.watch(b.nameInput,value->vm.set("name",value));Ui.watch(b.teacherInput,value->vm.set("teacher",value));
        Ui.watch(b.colorHex,value->{
            vm.set("subjectColorHex",value);
            try{vm.set("colorValue",AppearancePalette.parseHex(value));b.colorSpinner.setSelection(colorIndex());}
            catch(IllegalArgumentException ignored){ /* Keep the draft so validation can explain the format. */ }
        });
        b.colorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
            if(position<COLORS.length && vm.flag("initialized") && position!=colorIndex()){
                vm.set("colorValue",COLORS[position]);b.colorHex.setText(AppearancePalette.hex(COLORS[position]));
            }
        }public void onNothingSelected(AdapterView<?> parent){}});
        b.iconSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View view,int position,long id){vm.set("icon",position);}public void onNothingSelected(AdapterView<?> parent){}});
        b.saveButton.setOnClickListener(v->{if(vm.text("name").trim().isEmpty()){b.nameInput.setError(getString(R.string.core_required));return;}
            try{vm.saveSubject(AppearancePalette.parseHex(b.colorHex.getText().toString()));}
            catch(IllegalArgumentException invalid){b.colorHex.setError(getString(R.string.subject_color_invalid));}
        });
        b.deleteButton.setOnClickListener(v->new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.core_delete_subject).setMessage(R.string.core_subject_delete_hint).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.delete,(d,w)->vm.deleteSubject(vm.number("id",0))).show());
        vm.revision.observe(getViewLifecycleOwner(),revision->render());
        vm.busy.observe(getViewLifecycleOwner(),busy->{b.progress.setVisibility(busy?View.VISIBLE:View.GONE);enableForm();});
        vm.errors.observe(getViewLifecycleOwner(),event->{String error=event.take();if(error!=null)message(error);});
        vm.saved.observe(getViewLifecycleOwner(),event->{if(event.take()!=null){Toast.makeText(requireContext(),R.string.saved,Toast.LENGTH_SHORT).show();NavHostFragment.findNavController(this).navigateUp();}});
        vm.removed.observe(getViewLifecycleOwner(),event->{if(event.take()!=null)NavHostFragment.findNavController(this).navigateUp();});
        vm.loadSubject(getArguments()==null?0:getArguments().getLong("id"));
    }
    private void render(){
        b.nameInput.setText(vm.text("name"));b.teacherInput.setText(vm.text("teacher"));
        int color=(int)vm.number("colorValue",COLORS[0]);
        b.colorSpinner.setSelection(colorIndex());
        if(!vm.state.contains("subjectColorHex") || !vm.flag("initialized"))b.colorHex.setText(AppearancePalette.hex(color));
        else b.colorHex.setText(vm.text("subjectColorHex"));
        b.iconSpinner.setSelection(Math.max(0,Math.min(2,(int)vm.number("icon",0))));
        b.deleteButton.setVisibility(vm.number("id",0)==0?View.GONE:View.VISIBLE);enableForm();
    }
    private int colorIndex(){int color=(int)vm.number("colorValue",COLORS[0]);for(int i=0;i<COLORS.length;i++)if(COLORS[i]==color)return i;return COLORS.length;}
    private void enableForm() {
        boolean enabled=!vm.isBusy()&&vm.flag("initialized");
        b.saveButton.setEnabled(enabled);b.deleteButton.setEnabled(enabled);b.nameInput.setEnabled(enabled);
        b.teacherInput.setEnabled(enabled);b.colorSpinner.setEnabled(enabled);b.iconSpinner.setEnabled(enabled);
        b.colorHex.setEnabled(enabled);
    }
    @Override public void onDestroyView(){b=null;super.onDestroyView();}
}
