package com.tutpro.baresip;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.*;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.AutoCompleteTextView;
import android.view.*;
import android.util.Log;
import java.util.*;
import java.io.*;
import android.content.Context;

public class MainActivity extends AppCompatActivity {

    static Context mainActivityContext;
    static Boolean running = false;
    static AutoCompleteTextView callee;
    static RelativeLayout layout;
    static Button callButton;
    static Button holdButton;
    static ArrayList<Account> Accounts = new ArrayList<>();
    static ArrayList<String> AoRs = new ArrayList<>();
    static ArrayList<Integer> Images = new ArrayList<>();
    static AccountSpinnerAdapter AccountAdapter = null;
    static ArrayAdapter<String> CalleeAdapter = null;
    static ArrayList<Call> In = new ArrayList<>();
    static ArrayList<Call> Out = new ArrayList<>();

    private static final int RECORD_AUDIO_PERMISSION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainActivityContext = getApplicationContext();

        Spinner AoRSpinner = (Spinner) findViewById(R.id.AoRList);
        Log.i("Baresip", "Setting AccountAdapter");
        AccountAdapter = new AccountSpinnerAdapter(getApplicationContext(), AoRs, Images);
        AoRSpinner.setAdapter(AccountAdapter);
        AoRSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String aor = AoRs.get(position);
                Log.i("Baresip", "Setting " + aor + " current");
                ua_current_set(aor);
                ArrayList<Call> out = uaCalls(Out, ua_current());
                if (out.size() == 0) {
                    callee.setHint("Callee");
                    callButton.setText("Call");
                    holdButton.setVisibility(View.INVISIBLE);
                } else {
                    callee.setText(out.get(0).getPeerURI());
                    callButton.setText(out.get(0).getStatus());
                    if (out.get(0).getStatus() == "Hangup") {
                        if (out.get(0).getHold()) {
                            holdButton.setText("Unhold");
                        } else {
                            holdButton.setText("Hold");
                        }
                        holdButton.setVisibility(View.VISIBLE);
                    } else {
                        holdButton.setVisibility(View.INVISIBLE);
                    }
                }
                ArrayList<Call> in = uaCalls(In, ua_current());
                int view_count = layout.getChildCount();
                Log.d("Baresip", "View count is " + view_count);
                if (view_count > 5) {
                    layout.removeViews(5, view_count - 5);
                }
                for (Call c: in) {
                    for (int call_index = 0; call_index < In.size(); call_index++) {
                        addCallViews(c,(call_index + 1) * 10);
                    }
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Log.i("Baresip", "Nothing selected");
            }
        });

        String[] assets = {"accounts", "contacts", "config", "busy.wav", "callwaiting.wav",
                "error.wav", "message.wav", "notfound.wav", "ring.wav", "ringback.wav"};
        final String path = mainActivityContext.getFilesDir().getPath();
        Log.d("Baresip", "path is: " + path);
        File file;
        file = new File(path);
        if (!file.exists()) {
            Log.d("Baresip", "Creating baresip directory");
            try {
                new File(path).mkdirs();
            } catch (Error e) {
                Log.e("Baresip", "Failed to create directory: " +
                        e.toString());
            }
        }
        for (String a : assets) {
            file = new File(path + "/" + a);
            if (!file.exists()) {
                Log.d("Baresip", "Copying asset " + a);
                copyAsset(a, path + "/" + a);
            } else {
                Log.d("Baresip", "Asset " + a + " already copied");
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d("Baresip", "Baresip does not have RECORD_AUDIO permission");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_PERMISSION);
        }

        if (!running) {
            Log.i("Baresip", "Starting Baresip with path " + path);
            new Thread(new Runnable() {
                public void run() {
                    baresipStart(path);
                }
            }).start();
            running = true;
        }

        layout = (RelativeLayout) findViewById(R.id.mainActivityLayout);

        callee = (AutoCompleteTextView)findViewById(R.id.callee);
        EditContactsActivity.updateContactsAndNames();
        CalleeAdapter = new ArrayAdapter<String>
                (this,android.R.layout.select_dialog_item, EditContactsActivity.Names);
        callee.setThreshold(2);
        callee.setAdapter(CalleeAdapter);

        callButton = (Button)findViewById(R.id.callButton);
        callButton.setText("Call");
        callButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ua = ua_current();
                if (callButton.getText().toString().equals("Call")) {
                    String callee = ((EditText) findViewById(R.id.callee)).getText().toString();
                    String uri = EditContactsActivity.findContactURI(callee);
                    if (!uri.startsWith("sip:")) uri = "sip:" + uri;
                    if (!uri.contains("@")) {
                        String aor = ua_aor(ua);
                        String host = aor.substring(aor.indexOf("@") + 1);
                        uri = uri + "@" + host;
                    }
                    ((EditText) findViewById(R.id.callee)).setText(uri);
                    Log.i("Baresip", "Calling " + uri);
                    String call = ua_connect(uri);
                    if (!call.equals("")) {
                        Log.i("Baresip", "Adding outgoing call " + ua + "/" + call +
                                "/" + uri);
                        Out.add(new Call(ua, call, uri,"Cancel"));
                        callButton.setText("Cancel");
                    }
                } else {
                    Log.i("Baresip", "Hanging up " +
                            ((EditText) findViewById(R.id.callee)).getText());
                    ua_hangup(ua, "", 486, "Rejected");
                }
            }
        });

        holdButton = (Button)findViewById(R.id.holdButton);
        holdButton.setVisibility(View.INVISIBLE);
        holdButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (holdButton.getText().toString().equals("Hold")) {
                    Log.i("Baresip", "Holding up " +
                            ((EditText) findViewById(R.id.callee)).getText());
                    call_hold(Out.get(0).getCall());
                    Out.get(0).setHold(true);
                    holdButton.setText("Unhold");
                } else {
                    Log.i("Baresip", "Unholding " +
                            ((EditText) findViewById(R.id.callee)).getText());
                    call_unhold(Out.get(0).getCall());
                    Out.get(0).setHold(false);
                    holdButton.setText("Hold");
                }
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.d("Baresip", "Screen orientation change to landscape");
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
            Log.d("Baresip", "Screen orientation change to portrait");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case RECORD_AUDIO_PERMISSION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Baresip", "RECORD_AUDIO permission granted");
                } else {
                    Log.d("Baresip", "RECORD_AUDIO permission NOT granted");
                }
                return;
            }
            default:
                Log.e("Baresip", "Unknown permissions request code: " + requestCode);
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        Intent i;
        switch (item.getItemId()) {
            case R.id.accounts:
                i = new Intent(this, EditAccountsActivity.class);
                startActivityForResult(i, 1);
                return true;
            case R.id.contacts:
                startActivity(new Intent(MainActivity.this,
                        EditContactsActivity.class));

                return true;
            case R.id.config:
                i = new Intent(this, EditConfigActivity.class);
                startActivityForResult(i, 2);
                return true;
            case R.id.about:
                startActivity(new Intent(MainActivity.this,
                        AboutActivity.class));
                return true;
            case R.id.quit:
                if (running) {
                    Log.d("Baresip", "Stopping");
                    baresipStop();
                    Accounts.clear();
                    AoRs.clear();
                    Images.clear();
                    running = false;
                }
                finish();
                System.exit(0);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            if(resultCode == RESULT_OK) {
                AlertDialog alertDialog = new AlertDialog.Builder(this).create();
                alertDialog.setTitle("Alert");
                alertDialog.setMessage("You need to restart baresip in order to activate saved accounts!");
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                alertDialog.show();
            }
            if (resultCode == RESULT_CANCELED) {
                Log.d("Baresip", "Edit accounts canceled");
            }
        }
        if (requestCode == 2) {
            if(resultCode == RESULT_OK) {
                Utils.alertView(this,
                        "You need to restart baresip in order to activate saved config!");
                reload_config();
            }
            if (resultCode == RESULT_CANCELED) {
                Log.d("Baresip", "Edit accounts canceled");
            }
        }
    }

    public void addAccount(String ua) {
        String aor = ua_aor(ua);
        Log.d("Baresip", "Adding account " + ua + " with AoR " + aor);
        Accounts.add(new Account(ua, aor));
        AoRs.add(aor);
        Images.add(R.drawable.yellow);
        runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AccountAdapter.notifyDataSetChanged();
                }
            });
    }

    private void copyAsset(String asset, String path) {
        try {
            AssetManager assetManager = getAssets();
            InputStream is = assetManager.open(asset);
            OutputStream os = new FileOutputStream(path);
            byte [] buffer = new byte[512];
            int byteRead;
            while ((byteRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, byteRead);
            }
        } catch (IOException e) {
            Log.e("Baresip", "Failed to read asset " + asset + ": " +
                    e.toString());
        }
    }

    private void addCallViews(final Call call, int id) {
        Log.d("Baresip", "Creating new Incoming textview at " + id);
        TextView caller_heading = new TextView(mainActivityContext);
        caller_heading.setText("Incoming call from ...");
        caller_heading.setTextColor(Color.BLACK);
        caller_heading.setTextSize(20);
        caller_heading.setPadding(10, 20, 0, 0);
        caller_heading.setId(id);
        LayoutParams heading_params = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        if (id == 10)
            heading_params.addRule(RelativeLayout.BELOW, callButton.getId());
        else
            heading_params.addRule(RelativeLayout.BELOW,id - 10 + 3);
        caller_heading.setLayoutParams(heading_params);
        layout.addView(caller_heading);

        TextView caller_uri = new TextView(mainActivityContext);
        caller_uri.setText(In.get(In.size() - 1).getPeerURI());
        caller_uri.setTextColor(Color.GREEN);
        caller_uri.setTextSize(20);
        caller_uri.setPadding(10, 10, 0, 10);
        Log.d("Baresip", "Creating new caller textview at " + (id + 1));
        caller_uri.setId(id + 1);
        LayoutParams caller_uri_params = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        caller_uri_params.addRule(RelativeLayout.BELOW, id);
        caller_uri.setLayoutParams(caller_uri_params);
        layout.addView(caller_uri);

        Button answer_button = new Button(mainActivityContext);
        answer_button.setText(call.getStatus());
        answer_button.setBackgroundResource(android.R.drawable.btn_default);
        answer_button.setTextColor(Color.BLACK);
        Log.d("Baresip", "Creating new answer button at " + (id + 2));
        answer_button.setId(id + 2);
        answer_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String call_ua = call.getUA();
                String call_call = call.getCall();
                switch (((Button)v).getText().toString()) {
                    case "Answer":
                        Log.i("Baresip", "UA " + call_ua + " accepting incoming call " +
                                call_call);
                        ua_answer(call_ua, call_call);
                        final int final_in_index = callIndex(In, call_ua, call_call);
                        if (final_in_index >= 0) {
                            Log.d("Baresip", "Updating Hangup and Hold");
                            In.get(final_in_index).setStatus("Hangup");
                            In.get(final_in_index).setHold(false);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    final int answer_id = (final_in_index + 1) * 10 + 2;
                                    Button answer_button = (Button)layout.findViewById(answer_id);
                                    answer_button.setText("Hangup");
                                    Button reject_button = (Button)layout.findViewById(answer_id + 1);
                                    reject_button.setText("Hold");
                                }
                            });
                        }
                        break;
                    case "Hangup":
                        Log.i("Baresip", "UA " + call_ua + " hanging up call " +
                                call_call);
                        ua_hangup(call_ua, call_call,200, "OK");
                        break;
                    default:
                        Log.e("Baresip", "Invalid answer button text: " +
                                ((Button)v).getText().toString());
                        break;
                }
            }
        });
        LayoutParams answer_button_params = new LayoutParams(200,
                LayoutParams.WRAP_CONTENT);
        answer_button_params.addRule(RelativeLayout.BELOW, id + 1);
        answer_button_params.setMargins(3, 10, 0, 0);
        answer_button.setLayoutParams(answer_button_params);
        layout.addView(answer_button);

        Button reject_button = new Button(mainActivityContext);
        if (call.getStatus() == "Answer") {
            reject_button.setText("Reject");
        } else {
            if (call.getHold()) {
                reject_button.setText("Unhold");
            } else {
                reject_button.setText("Hold");
            }
        }
        reject_button.setBackgroundResource(android.R.drawable.btn_default);
        reject_button.setTextColor(Color.BLACK);
        Log.d("Baresip", "Creating new reject button at " + (id + 3));
        reject_button.setId(id + 3);
        reject_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (((Button)v).getText().toString()) {
                    case "Reject":
                        Log.i("Baresip", "UA " + call.getUA() +
                                " rejecting incoming call " + call.getCall());
                        ua_hangup(call.getUA(), call.getCall(), 486, "Rejected");
                        break;
                    case "Hold":
                        call_hold(call.getCall());
                        ((Button)v).setText("Unhold");
                        call.setHold(true);
                        break;
                    case "Unhold":
                        call_unhold(call.getCall());
                        ((Button)v).setText("Hold");
                        call.setHold(false);
                        break;
                }
            }
        });
        LayoutParams reject_button_params = new LayoutParams(200,
                LayoutParams.WRAP_CONTENT);
        reject_button_params.addRule(RelativeLayout.BELOW, id + 1);
        reject_button_params.setMargins(225, 10, 0, 0);
        reject_button.setLayoutParams(reject_button_params);
        layout.addView(reject_button);
    }

    private int callIndex(ArrayList<Call> calls, String ua, String call) {
        for (int i = 0; i < calls.size(); i++) {
            if (calls.get(i).getUA().equals(ua) && calls.get(i).getCall().equals(call))
                return i;
        }
        return -1;
    }

    private ArrayList<Call> uaCalls(ArrayList<Call> calls, String ua) {
        ArrayList<Call> result = new ArrayList<>();
        for (int i = 0; i < calls.size(); i++) {
            if (calls.get(i).getUA().equals(ua)) result.add(calls.get(i));
        }
        return result;
    }

    private void updateStatus(String event, final String ua, final String call) {
        String aor = ua_aor(ua);
        int index, call_index;

        Log.d("Baresip", "Handling event " + event + " for " + ua + "/" + call + "/" +
                aor);
        for (index = 0; index < Accounts.size(); index++) {
            if (Accounts.get(index).getAoR().equals(aor)) {
                Log.d("Baresip", "Found AoR at index " + index);
                switch (event) {
                    case "registering":
                    case "unregistering":
                        // Log.d("Baresip", "Setting status to yellow");
                        break;
                    case "registered":
                        Log.d("Baresip", "Setting status to green");
                        Accounts.get(index).setStatus("OK");
                        AoRs.set(index, aor);
                        Images.set(index, R.drawable.green);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                AccountAdapter.notifyDataSetChanged();
                            }
                        });
                        break;
                    case "registering failed":
                        Log.d("Baresip", "Setting status to red");
                        Accounts.get(index).setStatus("FAIL");
                        AoRs.set(index, aor);
                        Images.set(index, R.drawable.red);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                AccountAdapter.notifyDataSetChanged();
                            }
                        });
                        break;
                    case "call ringing":
                        break;
                    case "call established":
                        Log.d("Baresip", "Out call index is " + (Out.size() - 1));
                        int out_index = callIndex(Out, ua, call);
                        if (out_index >= 0) {
                            Log.d("Baresip", "Update Hangup and Hold");
                            Out.get(out_index).setStatus("Hangup");
                            Out.get(out_index).setHold(false);
                            if (ua.equals(ua_current())) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        callButton.setText("Hangup");
                                        holdButton.setText("Hold");
                                        holdButton.setVisibility(View.VISIBLE);
                                    }
                                });
                            }
                            break;
                        }
                        Log.e("Baresip", "Unknown call " + ua + "/" + call +
                                " established");
                        break;
                    case "call incoming":
                        final String peer_uri = call_peeruri(call);
                        Log.d("Baresip", "Incoming call " + ua + "/" + call + "/" +
                                peer_uri);

                        final Call new_call = new Call(ua, call, peer_uri, "Answer");
                        In.add(new_call);
                        Log.d("Baresip", "Current UA is " + ua_current());
                        if (ua.equals(ua_current())) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    addCallViews(new_call, In.size() * 10);
                                }
                            });
                        }
                        break;
                    case "call closed":
                        call_index = callIndex(In, ua, call);
                        if (call_index != -1) {
                            Log.d("Baresip", "Removing inbound call " + ua + "/" +
                                    call + "/" + In.get(index).getPeerURI());
                            final int view_id = (call_index + 1) * 10;
                            final int remove_count = In.size() - call_index;
                            final int final_call_index = call_index;
                            In.remove(call_index);
                            Log.d("Baresip", "Current UA is " + ua_current());
                            if (ua.equals(ua_current())) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        View caller_heading = layout.findViewById(view_id);
                                        int view_index = layout.indexOfChild(caller_heading);
                                        Log.d("Baresip", "Index of caller heading is " +
                                                view_index);
                                        layout.removeViews(view_index, 4 * remove_count);
                                        for (int i = final_call_index; i < In.size(); i++) {
                                            addCallViews(In.get(i), (i + 1) * 10);
                                        }
                                    }
                                });
                            }
                            break;
                        }
                        call_index = callIndex(Out, ua, call);
                        if (call_index != -1) {
                            Log.d("Baresip", "Removing called call " + ua + "/" +
                                    call + "/" + Out.get(index).getPeerURI());
                            Out.remove(call_index);
                            if (ua.equals(ua_current())) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        callButton.setText("Call");
                                        callButton.setEnabled(true);
                                        holdButton.setVisibility(View.INVISIBLE);
                                    }
                                });
                            }
                            break;
                        }
                        Log.e("Baresip", "Unknown call " + ua + "/" + call +
                                " closed");
                        break;
                    default:
                        Log.d("Baresip", "Unknown event '" + event + "'");
                        break;
                }
            }
        }
    }

    public native void baresipStart(String path);
    public native void baresipStop();
    public native String ua_aor(String ua);
    public native Boolean ua_isregistered(long ua_ptr);
    public native String ua_call(String ua);
    public native String call_peeruri(String call);
    public native String ua_current();
    public static native void ua_current_set(String s);
    public native String ua_connect(String s);
    public native void ua_answer(String ua, String call);
    public native Integer call_hold(String call);
    public native Integer call_unhold(String call);
    public native void ua_hangup(String ua, String call, int code, String reason);
    static public native void contacts_remove();
    static public native void contact_add(String contact);
    static public native int reload_config();

    static {
        System.loadLibrary("baresip");
    }

}
