package kz.uniplan.app.ui.profile;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.SavedStateHandle;
import kz.uniplan.app.UniPlanApp;
import kz.uniplan.app.util.Appearance;
import kz.uniplan.app.util.AppearancePalette;
import kz.uniplan.app.util.SettingsStore;

/** Draft colors remain separate from saved settings until Apply is pressed. */
public final class AppearanceViewModel extends AndroidViewModel {
    private final SavedStateHandle state;
    private final SettingsStore settings;
    public AppearanceViewModel(@NonNull Application application,SavedStateHandle state){
        super(application);this.state=state;settings=UniPlanApp.get(application).prefs();
        if(!state.contains("appearanceNight"))state.set("appearanceNight",settings.theme()==2||settings.theme()==0&&Appearance.isNight(application));
        initialize(false);initialize(true);
    }
    private String key(boolean night,String name){return (night?"dark_":"light_")+name;}
    private String key(String name){return key(night(),name);}
    private void initialize(boolean night){if(!state.contains(key(night,"background")))replace(night,settings.appearance(night));}
    private void replace(boolean night,AppearancePalette p){
        state.set(key(night,"background"),AppearancePalette.hex(p.background));state.set(key(night,"card"),AppearancePalette.hex(p.card));state.set(key(night,"accent"),AppearancePalette.hex(p.accent));
        state.set(key(night,"good_background"),p.background);state.set(key(night,"good_card"),p.card);state.set(key(night,"good_accent"),p.accent);state.set(key(night,"corner"),p.cornerDp);
    }
    public boolean night(){return Boolean.TRUE.equals(state.get("appearanceNight"));}
    public void setNight(boolean night){state.set("appearanceNight",night);}
    public String hex(String field){return state.get(key(field));}
    public void setHex(String field,String value){state.set(key(field),value);try{state.set(key("good_"+field),AppearancePalette.parseHex(value));}catch(IllegalArgumentException ignored){/* Keep the last valid live-preview value. */}}
    public int corner(){Integer value=state.get(key("corner"));return value==null?18:value;}
    public void setCorner(int radius){state.set(key("corner"),Math.max(0,Math.min(32,radius)));}
    public AppearancePalette palette(){return new AppearancePalette(state.get(key("good_background")),state.get(key("good_card")),state.get(key("good_accent")),corner());}
    public boolean valid(String field){try{AppearancePalette.parseHex(hex(field));return true;}catch(IllegalArgumentException e){return false;}}
    public boolean valid(){return valid("background")&&valid("card")&&valid("accent");}
    public void preset(int index){replace(night(),AppearancePalette.preset(index,night()));}
    public int presetSelection(){AppearancePalette p=palette();for(int i=0;i<4;i++){AppearancePalette candidate=AppearancePalette.preset(i,night());if(p.background==candidate.background&&p.card==candidate.card&&p.accent==candidate.accent)return i+1;}return 0;}
    public void save(){if(!valid())throw new IllegalStateException("Invalid appearance input");settings.saveAppearance(night(),palette());}
    public void reset(){settings.resetAppearance(night());replace(night(),settings.appearance(night()));}
}
