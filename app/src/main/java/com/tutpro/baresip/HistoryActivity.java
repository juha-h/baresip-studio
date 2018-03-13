package com.tutpro.baresip;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

public class HistoryActivity extends AppCompatActivity {

    ArrayList<HistoryRow> uaHistory = new ArrayList<>();
    ArrayList<Integer> posAtHistory = new ArrayList<>();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        final ListView listview = (ListView) findViewById(R.id.history);

        generate_ua_history();

        final HistoryListAdapter adapter = new HistoryListAdapter(this, uaHistory);
        listview.setAdapter(adapter);
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
                HistoryRow row = uaHistory.get(position);
                Intent i = new Intent();
                i.putExtra("peer_uri", row.getPeerURI());
                setResult(RESULT_OK, i);
                finish();
            }
        });

        listview.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int pos, long id) {
                DialogInterface.OnClickListener dialogClickListener =
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which){
                            case DialogInterface.BUTTON_POSITIVE:
                                MainActivity.History.remove((posAtHistory.get(pos)).intValue());
                                generate_ua_history();
                                if (uaHistory.size() == 0) {
                                    Intent i = new Intent();
                                    setResult(RESULT_CANCELED, i);
                                    finish();
                                }
                                adapter.notifyDataSetChanged();
                                break;
                            case DialogInterface.BUTTON_NEGATIVE:
                                break;
                        }
                    }
                };
                AlertDialog.Builder builder =
                        new AlertDialog.Builder(HistoryActivity.this,
                                R.style.Theme_AppCompat);
                builder.setMessage("Do you want to delete " +
                        MainActivity.History.get(pos).getPeerURI() + "?")
                        .setPositiveButton("Yes", dialogClickListener)
                        .setNegativeButton("No", dialogClickListener).show();
                return true;
            }
        });

        listview.setLongClickable(true);
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

    private void generate_ua_history() {
        uaHistory.clear();
        posAtHistory.clear();
        for (Integer i = MainActivity.History.size() - 1; i >= 0; i--) {
            History h = MainActivity.History.get(i);
            if (h.getAoR().equals(MainActivity.ua_aor(MainActivity.ua_current()))) {
                String time;
                if (isToday(h.getTime())) {
                    SimpleDateFormat fmt = new SimpleDateFormat("HH:mm");
                    time = fmt.format(h.getTime().getTime());
                } else {
                    SimpleDateFormat fmt = new SimpleDateFormat("MMM dd");
                    time = fmt.format(h.getTime().getTime());
                }
                if (h.getDirection().equals("in")) {
                    if (h.getConnected()) {
                        uaHistory.add(new HistoryRow(h.getPeerURI(), R.drawable.arrow_down_green, time));
                    } else {
                        uaHistory.add(new HistoryRow(h.getPeerURI(), R.drawable.arrow_down_red, time));
                    }
                } else {
                    if (h.getConnected()) {
                        uaHistory.add(new HistoryRow(h.getPeerURI(), R.drawable.arrow_up_green, time));
                    } else {
                        uaHistory.add(new HistoryRow(h.getPeerURI(), R.drawable.arrow_up_red, time));
                    }
                }
                posAtHistory.add(i);
            }
        }
    }

    private Boolean isToday(GregorianCalendar time) {
        GregorianCalendar now = new GregorianCalendar();
        return now.get(Calendar.YEAR) == time.get(Calendar.YEAR) &&
                now.get(Calendar.MONTH) == time.get(Calendar.MONTH) &&
                now.get(Calendar.DAY_OF_MONTH) == time.get(Calendar.DAY_OF_MONTH);
    }

}
