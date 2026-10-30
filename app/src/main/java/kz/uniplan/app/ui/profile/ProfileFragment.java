package kz.uniplan.app.ui.profile;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import kz.uniplan.app.R;
import kz.uniplan.app.data.auth.AuthRepository;
import kz.uniplan.app.databinding.FragmentProfileBinding;
import kz.uniplan.app.ui.common.BaseFragment;
import kz.uniplan.app.ui.common.Ui;
public final class ProfileFragment extends BaseFragment {
    private FragmentProfileBinding binding;
    private ProfileViewModel model;
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup container,Bundle state) { binding=FragmentProfileBinding.inflate(inflater,container,false); return binding.getRoot(); }
    @Override public void onViewCreated(View view,Bundle state) {
        super.onViewCreated(view,state); model=new ViewModelProvider(this).get(ProfileViewModel.class);
        AuthRepository.Session session=app().auth().current(); if(session==null) return;
        binding.email.setText(app().auth().isDemo()?getString(R.string.demo_local_profile,session.displayName):session.email);
        model.profile().observe(getViewLifecycleOwner(),profile->{ if(profile!=null && !Boolean.TRUE.equals(model.state.get("initialized"))) { model.state.set("initialized",true); model.state.set("name",profile.displayName); model.state.set("group",profile.studyGroup); binding.name.setText(profile.displayName); binding.group.setText(profile.studyGroup); } });
        String restoredName=model.state.get("name"), restoredGroup=model.state.get("group");
        binding.name.setText(restoredName); binding.group.setText(restoredGroup);
        Ui.watch(binding.name,value->model.state.set("name",value)); Ui.watch(binding.group,value->model.state.set("group",value));
        binding.saveProfile.setOnClickListener(v->model.save(binding.name.getText().toString(),binding.group.getText().toString()));
        binding.subjects.setOnClickListener(v->NavHostFragment.findNavController(this).navigate(R.id.subjectListFragment));
        binding.appearance.setOnClickListener(v->NavHostFragment.findNavController(this).navigate(R.id.appearanceFragment));
        binding.seed.setVisibility(app().auth().isDemo()?View.VISIBLE:View.GONE);
        binding.seed.setOnClickListener(v->new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.profile_seed).setMessage(R.string.profile_seed_help).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.add,(d,w)->model.seed()).show());
        binding.signOut.setOnClickListener(v->new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.sign_out).setMessage(R.string.profile_signout_help).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.sign_out,(d,w)->app().auth().signOut()).show());
        binding.about.setOnClickListener(v->new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.about_title).setMessage(R.string.about_text).setPositiveButton(R.string.ok,null).show());
        binding.learn.setOnClickListener(v->{ try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://developer.android.com/guide"))); } catch(ActivityNotFoundException e) { message("unknown"); } });
        setupSpinner(binding.theme,R.array.theme_options,app().prefs().theme(),position->{ if(position!=app().prefs().theme()) app().prefs().setTheme(position); });
        setupSpinner(binding.language,R.array.language_options,app().prefs().language().equals("ru")?1:0,position->{ String language=position==1?"ru":"kk"; if(!app().prefs().language().equals(language)) app().prefs().setLanguage(language); });
        setupSpinner(binding.noteLayout,R.array.note_layout_options,app().prefs().noteGrid(session.uid)?1:0,position->app().prefs().setNoteGrid(session.uid,position==1));
        model.busy.observe(getViewLifecycleOwner(),busy->{ binding.saveProfile.setEnabled(!busy); binding.seed.setEnabled(!busy); binding.progress.setVisibility(busy?View.VISIBLE:View.GONE); });
        model.result.observe(getViewLifecycleOwner(),result->{ if(result==null) return; if(result.equals("saved")||result.startsWith("seeded")) Toast.makeText(requireContext(),result.equals("saved")?R.string.saved:result.equals("seeded")?R.string.profile_seed_done:R.string.profile_seed_already,Toast.LENGTH_LONG).show(); else message(result); model.result.setValue(null); });
    }
    private void setupSpinner(android.widget.Spinner spinner,int array,int selection,java.util.function.IntConsumer action) {
        ArrayAdapter<CharSequence> adapter=ArrayAdapter.createFromResource(requireContext(),array,android.R.layout.simple_spinner_item); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter); spinner.setSelection(selection);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() { public void onItemSelected(AdapterView<?> p,View v,int position,long id) { action.accept(position); } public void onNothingSelected(AdapterView<?> p) {} });
    }
    @Override public void onDestroyView() { binding.theme.setOnItemSelectedListener(null); binding.language.setOnItemSelectedListener(null); binding.noteLayout.setOnItemSelectedListener(null); binding=null; super.onDestroyView(); }
}
