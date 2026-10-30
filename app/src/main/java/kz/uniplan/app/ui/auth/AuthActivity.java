package kz.uniplan.app.ui.auth;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.snackbar.Snackbar;
import kz.uniplan.app.R;
import kz.uniplan.app.UniPlanApp;
import kz.uniplan.app.databinding.ActivityAuthBinding;
import kz.uniplan.app.ui.MainActivity;
import kz.uniplan.app.ui.common.BaseActivity;
import kz.uniplan.app.ui.common.Ui;

public final class AuthActivity extends BaseActivity {
    private ActivityAuthBinding binding;
    private AuthViewModel model;
    private boolean navigated;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        binding=ActivityAuthBinding.inflate(getLayoutInflater()); setContentView(binding.getRoot()); Ui.insets(binding.getRoot());
        model=new ViewModelProvider(this).get(AuthViewModel.class);
        UniPlanApp app=UniPlanApp.get(this);
        boolean demo=app.auth().isDemo();
        binding.demoPanel.setVisibility(demo?View.VISIBLE:View.GONE);
        binding.credentials.setVisibility(demo?View.GONE:View.VISIBLE);
        binding.subtitle.setText(demo?R.string.auth_demo_description:R.string.auth_subtitle);
        binding.demoOne.setOnClickListener(v->model.demo(1)); binding.demoTwo.setOnClickListener(v->model.demo(2));
        binding.language.setText(app.prefs().language().equals("kk")?R.string.switch_russian:R.string.switch_kazakh);
        binding.language.setOnClickListener(v->app.prefs().setLanguage(app.prefs().language().equals("kk")?"ru":"kk"));
        binding.submit.setOnClickListener(v->model.submit(binding.name.getText().toString(),binding.email.getText().toString(),binding.password.getText().toString(),binding.repeat.getText().toString()));
        binding.toggle.setOnClickListener(v->{ model.toggle(); renderMode(); });
        binding.reset.setOnClickListener(v->model.reset(binding.email.getText().toString()));
        binding.resend.setVisibility(app.auth().supportsCloud()?View.VISIBLE:View.GONE);
        binding.resend.setOnClickListener(v->model.resend(binding.email.getText().toString()));
        binding.cancelRecovery.setOnClickListener(v->app.auth().signOut());
        model.busy.observe(this,busy->{ binding.progress.setVisibility(busy?View.VISIBLE:View.GONE); binding.submit.setEnabled(!busy); binding.reset.setEnabled(!busy); binding.toggle.setEnabled(!busy); binding.resend.setEnabled(!busy); });
        app.auth().recovering().observe(this,recovering->renderMode());
        model.notice.observe(this,notice->{ if(notice!=null) { Snackbar.make(binding.getRoot(),notice,Snackbar.LENGTH_LONG).show(); model.notice.setValue(null); } });
        app.auth().session().observe(this,session->{
            if(session!=null && !navigated) {
                navigated=true;
                Intent intent=new Intent(this,MainActivity.class);
                Bundle extras=new Bundle(); extras.putString("greeting",session.displayName); intent.putExtras(extras);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent); finish();
            }
        });
        renderMode();
        handleIntent(getIntent());
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent); }
    private void handleIntent(Intent intent) {
        if(intent.getData()!=null) {
            String uri=intent.getData().toString(); intent.setData(null); model.redirect(uri);
        }
    }
    private void renderMode() {
        boolean recovery=model.recovering(), register=model.registering()&&!recovery;
        binding.nameBox.setVisibility(register?View.VISIBLE:View.GONE); binding.repeatBox.setVisibility(register||recovery?View.VISIBLE:View.GONE);
        binding.emailBox.setVisibility(recovery?View.GONE:View.VISIBLE);
        binding.toggle.setVisibility(recovery?View.GONE:View.VISIBLE); binding.reset.setVisibility(recovery?View.GONE:View.VISIBLE);
        binding.resend.setVisibility(!recovery&&UniPlanApp.get(this).auth().supportsCloud()?View.VISIBLE:View.GONE);
        binding.cancelRecovery.setVisibility(recovery?View.VISIBLE:View.GONE);
        binding.submit.setText(recovery?R.string.auth_new_password:register?R.string.auth_register:R.string.auth_sign_in);
        binding.toggle.setText(register?R.string.auth_have_account:R.string.auth_create_account);
    }
    @Override protected void onDestroy() { binding=null; super.onDestroy(); }
}
