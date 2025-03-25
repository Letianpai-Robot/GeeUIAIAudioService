package com.rhj.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import com.rhj.speech.R;

public class SpeechClosedView extends RelativeLayout {
    public SpeechClosedView(Context context) {
        super(context);
        inits(context);
    }


    public SpeechClosedView(Context context, AttributeSet attrs) {
        super(context, attrs);
        inits(context);
    }

    public SpeechClosedView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        inits(context);
    }

    public SpeechClosedView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        inits(context);
    }

    private void inits(Context context) {
        inflate(context, R.layout.view_close_view,this);
    }

}
