package com.skonamonkey.smsms;

/**
 * Created by Ibnu Maksum 2020
 */

import static com.skonamonkey.smsms.layanan.PushService.context;
import static com.skonamonkey.smsms.layanan.PushService.writeLog;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuItemCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.skonamonkey.smsms.Utils.Fungsi;
import com.skonamonkey.smsms.data.LogAdapter;
import com.skonamonkey.smsms.data.LogLine;
import com.skonamonkey.smsms.data.PaginationListener;
import com.skonamonkey.smsms.layanan.BackgroundService;
import com.skonamonkey.smsms.layanan.PushService;
import com.skonamonkey.smsms.layanan.UssdService;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private boolean serviceActive = false;
    TextView info;
    String infoTxt = "";
    RecyclerView recyclerview;
    LogAdapter adapter;
    SwipeRefreshLayout swipe;
    EditText editTextSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        recyclerview = findViewById(R.id.recyclerview);
        editTextSearch = findViewById(R.id.editTextSearch);
        swipe = findViewById(R.id.swipe);
        info = findViewById(R.id.text);
        info.setText("Click Me to Show Configuration");
        info.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                info.setText(infoTxt);
            }
        });
        swipe.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                adapter.getNewData();
                info.setText("Click Me to Show Configuration");
                swipe.setRefreshing(false);
            }
        });
        Dexter.withContext(this)
                .withPermissions(
                        Manifest.permission.RECEIVE_BOOT_COMPLETED,
                        Manifest.permission.GET_ACCOUNTS,
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.WAKE_LOCK,
                        Manifest.permission.ACCESS_NETWORK_STATE,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Manifest.permission.CALL_PHONE
                ).withListener(new MultiplePermissionsListener() {
                    @Override public void onPermissionsChecked(MultiplePermissionsReport report) {
                        if(report.areAllPermissionsGranted()){
                            Fungsi.log("All Permission granted");
                        }else if(report.isAnyPermissionPermanentlyDenied()){
                            Fungsi.log("Some Permission not granted");
                        }
                        Dexter.withContext(MainActivity.this)
                                .withPermissions(
                                        Manifest.permission.SEND_SMS,
                                        Manifest.permission.RECEIVE_SMS
                                ).withListener(new MultiplePermissionsListener() {
                                    @Override public void onPermissionsChecked(MultiplePermissionsReport report) {
                                        if(report.areAllPermissionsGranted()){
                                            Fungsi.log("All SMS Permission granted");
                                        }else if(report.isAnyPermissionPermanentlyDenied()){
                                            Fungsi.log("Some Permission not granted");
                                        }
                                    }
                                    @Override public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {/* ... */}
                                }).check();
                    }
                    @Override public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {/* ... */}
                }).check();
        updateInfo();

        if(getSharedPreferences("pref",0).getBoolean("gateway_on",true))
            checkServices();

        recyclerview.setHasFixedSize(true);
        // use a linear layout manager
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerview.setLayoutManager(layoutManager);
        adapter = new LogAdapter();
        recyclerview.setAdapter(adapter);
        adapter.reload();
        recyclerview.addOnScrollListener(new PaginationListener(layoutManager) {
            @Override
            protected void loadMoreItems() {
                recyclerview.post(new Runnable() {
                    @Override
                    public void run() {
                        adapter.nextData();
                    }
                });
            }
        });


        startService(new Intent(this, UssdService.class));

        editTextSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    recyclerview.post(new Runnable() {
                        @Override
                        public void run() {
                            adapter.search(editTextSearch.getText().toString());
                            editTextSearch.clearFocus();
                        }
                    });
                    handled = true;
                }
                return handled;
            }
        });

    }

    public void updateInfo(){
        SharedPreferences sp = getSharedPreferences("pref",0);
        infoTxt = "Your Device Secret \n\n"+sp.getString("secret",null)+
                "\n\nYour Device Token \n\n"+
                sp.getString("token","Generate Secure key to Get Token then Pull to Refresh")+"\n";
    }

    public void checkServices(){
        Fungsi.log("checkServices");
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("BackgroundService"));
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Fungsi.log("checkServices "+serviceActive);
                if(!serviceActive){
                    startService(new Intent(MainActivity.this, BackgroundService.class));
                }
            }
        },3000);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        MenuItem menuItem = menu.findItem(R.id.menu_gateway_switch);
        View view = MenuItemCompat.getActionView(menuItem);
        Switch switcha = view.findViewById(R.id.switchForActionBar);
        switcha.setChecked(getSharedPreferences("pref",0).getBoolean("gateway_on",true));
        //volley
        RequestQueue queue = Volley.newRequestQueue(this);
        switcha.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                getSharedPreferences("pref",0).edit().putBoolean("gateway_on",isChecked).apply();
                if(!isChecked){
                    Intent intent = new Intent("BackgroundService");
                    intent.putExtra("kill",true);
                    LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(intent);
                    Toast.makeText(MainActivity.this,"Gateway OFF",Toast.LENGTH_LONG).show();
                }else{
                    checkServices();
                    Toast.makeText(MainActivity.this,"Gateway ON",Toast.LENGTH_LONG).show();
                }
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {

            case R.id.menu_change_expired:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Change expired, by seconds");
                builder.setMessage("If you use Md5 for secret with time, if time expired, it will not send SMS");
                final EditText input = new EditText(this);
                input.setText(getSharedPreferences("pref",0).getInt("expired",3600)+"");
                input.setMaxLines(1);
                input.setInputType(InputType.TYPE_CLASS_PHONE | InputType.TYPE_TEXT_VARIATION_PHONETIC);
                builder.setView(input);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String ex = input.getText().toString();
                        try{
                            int exi = Integer.parseInt(ex);
                            if(exi<5){
                                exi = 5;
                            }
                            getSharedPreferences("pref",0).edit().putInt("expired", exi).commit();
                            Toast.makeText(MainActivity.this,"Expired changed",Toast.LENGTH_LONG).show();
                        }catch (Exception e){
                            //not numeric
                        }
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder.show();
                return true;
            case R.id.menu_change_secret:
                new AlertDialog.Builder(this)
                        .setTitle("Register / Create Device Secret")
                        .setMessage("This will Prevent Reporting of Previous SMS Messages.")
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                getSharedPreferences("pref",0).edit().putString("secret", UUID.randomUUID().toString()).commit();
                                updateInfo();
                                Toast.makeText(MainActivity.this,"Secret changed",Toast.LENGTH_LONG).show();
                            }
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
                return true;
            case R.id.menu_set_url:
                AlertDialog.Builder builder2 = new AlertDialog.Builder(this);
                builder2.setTitle("Change URL for receiving SMS");
                builder2.setMessage("Data will send using POST with parameter number and message and type=received/sent/delivered/ussd/token");
                final EditText input2 = new EditText(this);
                input2.setText(getSharedPreferences("pref",0).getString("urlPost",""));
                input2.setHint("https://skonamonkey.co.uk/heartbeat.php");
                input2.setMaxLines(1);
                input2.setInputType(InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT);
                builder2.setView(input2);
                builder2.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String urlPost = input2.getText().toString();
                        getSharedPreferences("pref",0).edit().putString("urlPost", urlPost).commit();
                        Toast.makeText(MainActivity.this,"SERVER URL changed",Toast.LENGTH_LONG).show();
                    }
                });
                builder2.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder2.show();
                return true;


            case R.id.menu_register_device:

                //volley
                RequestQueue queue = Volley.newRequestQueue(this);

                String tk = getSharedPreferences("pref", 0).getString("token", null);
                String devID = getSharedPreferences("pref", 0).getString("secret", null);
                String urlPost = getSharedPreferences("pref", 0).getString("urlPost", "https://smsapi.skonamonkey.co.uk");


                AlertDialog.Builder builder3 = new AlertDialog.Builder(this);
            builder3.setTitle("Set Device User");
            builder3.setMessage("Set User account this device is registered to");
            final EditText input3 = new EditText(this);
            input3.setText(getSharedPreferences("pref",0).getString("smUser",null));
            input3.setHint("email@domain.com");
            input3.setMaxLines(1);
            input3.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            builder3.setView(input3);
            builder3.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String smUser = input3.getText().toString();
                    String vURL = urlPost + "?src=smsmsAndroidApp&type=register&devID=" + devID;
                    getSharedPreferences("pref",0).edit().putString("smUser", smUser).commit();
                    //Toast.makeText(MainActivity.this,"SERVER URL changed",Toast.LENGTH_LONG).show();

                    //Start Volley Stuff

                    StringRequest postRequest = new StringRequest(Request.Method.POST, urlPost,
                            new Response.Listener<String>()
                            {
                                @Override
                                public void onResponse(String response) {
                                    // response
                                    Log.d("Response", response);
                                    Toast.makeText(MainActivity.this,"Registered to " + smUser,Toast.LENGTH_LONG).show();
                                    writeLog(response, context);
                                }
                            },
                            new Response.ErrorListener()
                            {
                                @Override
                                public void onErrorResponse(VolleyError error) {
                                    // error
                                    Log.d("Error.Response", "Unknown Error");
                                    Toast.makeText(MainActivity.this,"Error Registering Device ",Toast.LENGTH_LONG).show();
                                    writeLog("Error Registering Device :"+ devID , context);
                                }
                            }
                    ) {
                        @Override
                        protected Map<String, String> getParams()
                        {
                            Map<String, String>  params = new HashMap<String, String>();
                            params.put("src", "smsmsAndroidApp");
                            params.put("smUser", smUser);
                            params.put("devID", devID);
                            params.put("tk", tk);
                            params.put("type", "register");


                            return params;
                        }
                    };
                    queue.add(postRequest);




                    //end Volley  Stuff
                }
            });
            builder3.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });

            builder3.show();
            return true;



            case R.id.menu_clear_logs:
                new AlertDialog.Builder(this)
                        .setTitle("Clear Logs")
                        .setMessage("Are you sure?")
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                ObjectBox.get().boxFor(LogLine.class).removeAll();
                                adapter.reload();
                            }
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
                return true;
            case R.id.menu_ussd_set:
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
//              Toast.makeText(this,"Sudah Aktif",Toast.LENGTH_LONG).show();
                return true;
            case R.id.menu_ussd_test:
                callUssd();
                return true;
            case R.id.menu_battery_optimization:
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:"+BuildConfig.APPLICATION_ID)));
                return true;
        }
        return false;
    }

    public void callUssd(){
        AlertDialog.Builder builder2 = new AlertDialog.Builder(this);
        builder2.setTitle("SEND USSD");
        final EditText input2 = new EditText(this);
        input2.setText("*888#");
        input2.setHint("*888#");
        input2.setMaxLines(1);
        builder2.setView(input2);
        builder2.setPositiveButton("Call USSD", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String ussd = input2.getText().toString();
                Log.d("ussd","tel:"+ussd);
                if(PushService.context==null)
                    PushService.context = Aplikasi.app;
                PushService.queueUssd(ussd,1);
            }
        });
        builder2.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder2.show();
    }

    @Override
    protected void onResume() {
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver,new IntentFilter("MainActivity"));
        super.onResume();
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onPause();
    }

    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Fungsi.log("BroadcastReceiver received");
            if(intent.hasExtra("newMessage"))
                recyclerview.post(new Runnable() {
                    @Override
                    public void run() {
                        adapter.getNewData();
                    }
                });
            else if(intent.hasExtra("newToken"))
                updateInfo();
            else if(intent.hasExtra("kill") && intent.getBooleanExtra("kill",false)){
                Fungsi.log("BackgroundService KILLED");
                serviceActive = false;
            }else
                serviceActive = true;

        }
    };
}
