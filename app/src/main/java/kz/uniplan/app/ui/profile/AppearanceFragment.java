package kz.uniplan.app.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import kz.uniplan.app.R;
import kz.uniplan.app.databinding.FragmentAppearanceBinding;
import kz.uniplan.app.ui.common.BaseFragment;
import kz.uniplan.app.ui.common.Ui;
import kz.uniplan.app.util.Appearance;
import kz.uniplan.app.util.AppearancePalette;

public final class AppearanceFragment extends BaseFragment {
    private FragmentAppearanceBinding binding;
    private AppearanceViewModel model;
    private boolean rendering;
    @Override public View onCreateView(@NonNull LayoutInflater inflater,ViewGroup container,Bundle saved){binding=FragmentAppearanceBinding.inflate(inflater,container,false);return binding.getRoot();}
    @Override public void onViewCreated(@NonNull View view,Bundle saved){
        super.onViewCreated(view,saved);model=new ViewModelProvider(this).get(AppearanceViewModel.class);
        ArrayAdapter<CharSequence> presets=ArrayAdapter.createFromResource(requireContext(),R.array.appearance_presets,android.R.layout.simple_spinner_item);
        presets.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);binding.presets.setAdapter(presets);
        binding.presets.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent,View selected,int position,long id){if(!rendering&&position>0&&position!=model.presetSelection()){model.preset(position-1);render();}}
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        binding.paletteTheme.setOnCheckedChangeListener((group,id)->{if(!rendering){model.setNight(id==R.id.paletteDark);render();}});
        Ui.watch(binding.backgroundHex,value->{if(!rendering){model.setHex("background",value);preview();}});
        Ui.watch(binding.cardHex,value->{if(!rendering){model.setHex("card",value);preview();}});
        Ui.watch(binding.accentHex,value->{if(!rendering){model.setHex("accent",value);preview();}});
        binding.cornerSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean user){if(user){model.setCorner(progress);preview();}}
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });
        binding.applyAppearance.setOnClickListener(v->{if(!model.valid())return;model.save();Appearance.applyActivity(requireActivity());preview();Toast.makeText(requireContext(),R.string.appearance_saved,Toast.LENGTH_SHORT).show();});
        binding.resetAppearance.setOnClickListener(v->new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.appearance_reset).setMessage(R.string.appearance_reset_message)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.appearance_reset,(dialog,which)->{model.reset();render();Appearance.applyActivity(requireActivity());Toast.makeText(requireContext(),R.string.appearance_reset_done,Toast.LENGTH_SHORT).show();}).show());
        binding.previewButton.setOnClickListener(v->Toast.makeText(requireContext(),R.string.appearance_preview_action,Toast.LENGTH_SHORT).show());
        render();
    }
    private void render(){
        rendering=true;
        binding.paletteTheme.check(model.night()?R.id.paletteDark:R.id.paletteLight);
        binding.backgroundHex.setText(model.hex("background"));binding.cardHex.setText(model.hex("card"));binding.accentHex.setText(model.hex("accent"));
        binding.cornerSlider.setProgress(model.corner());binding.presets.setSelection(model.presetSelection());
        rendering=false;preview();
    }
    private void preview(){
        AppearancePalette palette=model.palette();
        binding.paletteHint.setText(model.night()?R.string.appearance_dark_hint:R.string.appearance_light_hint);
        binding.backgroundBox.setError(model.valid("background")?null:getString(R.string.appearance_hex_error));
        binding.cardBox.setError(model.valid("card")?null:getString(R.string.appearance_hex_error));
        binding.accentBox.setError(model.valid("accent")?null:getString(R.string.appearance_hex_error));
        binding.backgroundSwatch.setBackgroundColor(palette.background);binding.cardSwatch.setBackgroundColor(palette.card);binding.accentSwatch.setBackgroundColor(palette.accent);
        binding.cornerValue.setText(getString(R.string.appearance_corner_value,model.corner()));
        binding.applyAppearance.setEnabled(model.valid());
        boolean wasRendering=rendering;rendering=true;binding.presets.setSelection(model.presetSelection());rendering=wasRendering;
        Appearance.preview(binding.previewArea,palette);
    }
    @Override public void onDestroyView(){binding.presets.setOnItemSelectedListener(null);binding.cornerSlider.setOnSeekBarChangeListener(null);binding.paletteTheme.setOnCheckedChangeListener(null);binding=null;super.onDestroyView();}
}
