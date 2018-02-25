package com.tutpro.baresip;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;

public class EditContactsActivity extends AppCompatActivity {

    private EditText editText;
    static public ArrayList<Contact> Contacts = new java.util.ArrayList<>();
    static public ArrayList<String> Names = new java.util.ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_contacts);
        editText = (EditText)findViewById(R.id.editText);
        String path = getApplicationContext().getFilesDir().getAbsolutePath() + "/contacts";
        File file = new File(path);
        String content = Utils.getFileContents(file);
        Log.d("Baresip", "Contacts length is: " + content.length());
        editText.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.textsize));
        editText.setText(content, TextView.BufferType.EDITABLE);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.edit_menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        Intent i = new Intent(this, MainActivity.class);
        switch (item.getItemId()) {
        case R.id.save:
            String path = getApplicationContext().getFilesDir().getAbsolutePath() + "/contacts";
            File file = new File(path);
            Utils.putFileContents(file, editText.getText().toString());
            Log.d("Baresip", "Updated contacts file");
            MainActivity.contacts_remove();
            updateContactsAndNames();
            // MainActivity.CalleeAdapter.notifyDataSetChanged();
            MainActivity.CalleeAdapter = new ArrayAdapter<String>
                    (this,android.R.layout.select_dialog_item, Names);
            MainActivity.callee.setThreshold(2);
            MainActivity.callee.setAdapter(MainActivity.CalleeAdapter);
            i.putExtra("action", "save");
            setResult(RESULT_OK, i);
            finish();
            return true;
        case R.id.cancel:
            i.putExtra("action", "cancel");
            setResult(RESULT_OK, i);
            finish();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    static public void updateContactsAndNames() {
        String path = MainActivity.mainActivityContext.getFilesDir().getAbsolutePath() + "/contacts";
        File file = new File(path);
        String[] lines = Utils.getFileContents(file).split("\n");
        String name, uri;
        Contacts.clear();
        Names.clear();
        for (String line : lines) {
            line.trim();
            if (line.startsWith("#") || line.length() == 0) continue;
            String[] parts = line.split("\"");
            if (parts.length != 3) {
                Log.e("Baresip", "Invalid contacts line: " + line);
                continue;
            }
            name = parts[1];
            if (name.length() < 2) {
                Log.e("Baresip", "Too short contact display name: " + name);
                continue;
            }
            uri = parts[2].trim();
            if (!uri.startsWith("<") || !uri.contains(">")) {
                Log.e("Baresip", "Invalid contact uri: " + uri);
                continue;
            }
            MainActivity.contact_add("\"" + name + "\" " + uri);
            if (uri.indexOf(";access") > 0) continue;
            uri = uri.substring(1, uri.indexOf(">"));
            Log.d("Baresip", "Adding contact name/uri: " + name + "/" + uri);
            Contacts.add(new Contact(name, uri));
            Names.add(name);
        }
    }

    static public String findContactURI(String name) {
        for (Contact c : Contacts) {
            if (c.getName().equals(name)) return c.getURI();
        }
        return name;
    }

}

