package kz.uniplan.app.ui;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import kz.uniplan.app.R;
import kz.uniplan.app.UniPlanApp;
import kz.uniplan.app.data.auth.AuthRepository;
import kz.uniplan.app.databinding.ActivityMainBinding;
import kz.uniplan.app.ui.auth.AuthActivity;
import kz.uniplan.app.ui.common.BaseActivity;
import kz.uniplan.app.ui.common.Ui;
import kz.uniplan.app.ui.notes.NoteEditFragment;

public final class MainActivity extends BaseActivity {
    private ActivityMainBinding binding;
    private NavController nav;
    private NavHostFragment host;
    private boolean redirected;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        UniPlanApp app=UniPlanApp.get(this);
        AuthRepository.Session session=app.auth().current();
        if(session==null) { redirect(); return; }
        binding=ActivityMainBinding.inflate(getLayoutInflater()); setContentView(binding.getRoot()); Ui.insets(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        // Presentation only: identity is always read from the current AuthRepository session.
        String greeting=getIntent().getStringExtra("greeting");
        if(state==null && greeting!=null && !greeting.isEmpty()) android.widget.Toast.makeText(this,getString(R.string.auth_welcome,greeting),android.widget.Toast.LENGTH_SHORT).show();
        host=(NavHostFragment)getSupportFragmentManager().findFragmentById(R.id.navHost);
        nav=host.getNavController();
        AppBarConfiguration config=new AppBarConfiguration.Builder(R.id.homeFragment,R.id.tasksFragment,R.id.scheduleFragment,R.id.notesFragment,R.id.profileFragment).build();
        NavigationUI.setupActionBarWithNavController(this,nav,config);
        NavigationUI.setupWithNavController(binding.bottomNav,nav);
        binding.demoBanner.setVisibility(app.auth().isDemo()?View.VISIBLE:View.GONE);
        nav.addOnDestinationChangedListener((controller,destination,args)->{
            int id=destination.getId();
            boolean top=id==R.id.homeFragment||id==R.id.tasksFragment||id==R.id.scheduleFragment||id==R.id.notesFragment||id==R.id.profileFragment;
            binding.bottomNav.setVisibility(top?View.VISIBLE:View.GONE);
        });
        app.auth().session().observe(this,current->{ if(current!=session) redirect(); });
    }
    private void redirect() {
        if(redirected) return; redirected=true;
        if(binding!=null) binding.getRoot().setVisibility(View.INVISIBLE);
        Intent intent=new Intent(this,AuthActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent); finish();
    }
    @Override public boolean onSupportNavigateUp() {
        Fragment current=host.getChildFragmentManager().getPrimaryNavigationFragment();
        if(current instanceof NoteEditFragment) { ((NoteEditFragment)current).handleUp(); return true; }
        return nav.navigateUp()||super.onSupportNavigateUp();
    }
    @Override protected void onDestroy() { binding=null; super.onDestroy(); }
}
