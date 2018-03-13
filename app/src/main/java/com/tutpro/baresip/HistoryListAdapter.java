package com.tutpro.baresip;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

public class HistoryListAdapter extends ArrayAdapter<HistoryRow> {

    private Context context;
    private ArrayList<HistoryRow> rows;

    public HistoryListAdapter(Context context, ArrayList<HistoryRow> rows) {
        super(context, R.layout.history_row, rows);
        this.context = context;
        this.rows = rows;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        HistoryRow row = rows.get(position);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rowView = inflater.inflate(R.layout.history_row, parent, false);
        ImageView directionView = (ImageView) rowView.findViewById(R.id.direction);
        directionView.setImageResource(row.getDirection());
        TextView peerURIView = (TextView) rowView.findViewById(R.id.peer_uri);
        peerURIView.setText(row.getPeerURI());
        TextView timeView = (TextView) rowView.findViewById(R.id.time);
        timeView.setText(row.getTime());
        return rowView;
    }

}