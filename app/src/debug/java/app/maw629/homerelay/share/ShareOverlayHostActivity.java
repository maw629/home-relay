package app.maw629.homerelay.share;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

public class ShareOverlayHostActivity extends Activity {
    public static final int BACKGROUND_COLOR = 0xFF005E7A;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View background = new View(this);
        background.setBackgroundColor(BACKGROUND_COLOR);
        setContentView(background);
    }
}
