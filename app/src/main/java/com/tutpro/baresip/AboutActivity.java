package com.tutpro.baresip;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

public class AboutActivity extends AppCompatActivity {

    private TextView aboutView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        aboutView = (TextView)findViewById(R.id.aboutText);
        aboutView.setEnabled(false);
        aboutView.setText(getString(R.string.aboutText));
    }

}

