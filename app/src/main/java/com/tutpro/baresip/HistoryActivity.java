package com.tutpro.baresip;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class HistoryActivity extends AppCompatActivity {

	static ArrayList<String> history = new ArrayList<>();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        final ListView listview = (ListView) findViewById(R.id.history);

        ArrayList<String> history = new ArrayList<>();
		for (Integer i = MainActivity.History.size() - 1; i >= 0; i--) {
		    History h = MainActivity.History.get(i);
            if (h.getAoR().equals(MainActivity.ua_aor(MainActivity.ua_current()))) {
                if (!history.contains(h.getPeerURI())) history.add(h.getPeerURI());
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, history);
        listview.setAdapter(adapter);

		listview.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				Intent i = new Intent();
				i.putExtra("peer_uri", ((TextView) view).getText());
				setResult(RESULT_OK, i);
				finish();
			}
		});
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Log.d("Baresip", "Back array was pressed");
                Intent i = new Intent();
                setResult(RESULT_CANCELED, i);
                finish();
                break;
        }
        return true;
    }

}
