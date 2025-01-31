package com.marverenic.music.activity;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.marverenic.music.BuildConfig;
import com.marverenic.music.PlayerController;
import com.marverenic.music.R;
import com.marverenic.music.instances.Library;
import com.marverenic.music.instances.Song;
import com.marverenic.music.utils.Navigate;
import com.marverenic.music.utils.Prefs;
import com.marverenic.music.utils.Themes;

public abstract class BaseActivity extends AppCompatActivity implements View.OnClickListener,
        PlayerController.UpdateListener, PlayerController.ErrorListener {

    private static final boolean DEBUG = BuildConfig.DEBUG;

    // Used when resuming the Activity to respond to a potential theme change
    @StyleRes
    private int themeId;

    /**
     * @inheritDoc
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.i(getClass().toString(), "Called onCreate");

        Themes.setTheme(this);
        themeId = Themes.getTheme(this);
        super.onCreate(savedInstanceState);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Show a first start confirmation about privacy
        // This can't be done in the Application class because it's not allowed to show
        // AlertDialogs. Additionally, this check has to occur in every Activity since Jockey can
        // be started from sources other than its main Activity.

        final SharedPreferences prefs = Prefs.getPrefs(this);

        if (prefs.getBoolean(Prefs.SHOW_FIRST_START, true)) {
            final View messageView = getLayoutInflater().inflate(R.layout.alert_pref, null);
            final TextView message = (TextView) messageView.findViewById(R.id.alertMessage);
            final CheckBox pref = (CheckBox) messageView.findViewById(R.id.alertPref);

            message.setText(Html.fromHtml(getString(R.string.first_launch_detail)));
            message.setMovementMethod(LinkMovementMethod.getInstance());

            pref.setChecked(true);
            pref.setText(R.string.enable_additional_logging_detailed);

            AlertDialog privacyDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.first_launch_title)
                    .setView(messageView)
                    .setPositiveButton(R.string.action_agree,
                            new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            prefs.edit()
                                    .putBoolean(Prefs.SHOW_FIRST_START, false)
                                    .putBoolean(Prefs.ALLOW_LOGGING, pref.isChecked())
                                    .apply();

                            Library.scanAll(BaseActivity.this);
                        }
                    })
                    .setCancelable(false)
                    .create();

            privacyDialog.show();
            Themes.themeAlertDialog(privacyDialog);

        } else if (Library.isEmpty()) {
            Library.scanAll(this);
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantRequests) {
        super.onRequestPermissionsResult(requestCode, permissions, grantRequests);

        if (requestCode == Library.PERMISSION_REQUEST_ID && Library.hasRWPermission(this)) {
            Library.scanAll(this);
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void setContentView(@LayoutRes int layoutResId) {
        super.setContentView(layoutResId);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);

            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setHomeButtonEnabled(true);
                getSupportActionBar().setDisplayShowHomeEnabled(true);
            }
        }

        themeActivity();

        if (findViewById(R.id.miniplayer) != null) {
            findViewById(R.id.miniplayer).setOnClickListener(this);
            findViewById(R.id.playButton).setOnClickListener(this);
            findViewById(R.id.skipButton).setOnClickListener(this);
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.i(getClass().toString(), "Called onResume");

        if (themeId != Themes.getTheme(this)) {
            // If the theme was changed since this Activity was last started, recreate it
            recreate();
        } else {
            Themes.setApplicationIcon(this);
            PlayerController.registerUpdateListener(this);
            PlayerController.registerErrorListener(this);
            onUpdate();
            updateMiniplayer();
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void onPause() {
        super.onPause();
        if (DEBUG) Log.i(getClass().toString(), "Called onPause");
        PlayerController.unregisterUpdateListener(this);
        PlayerController.unregisterErrorListener(this);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (DEBUG) Log.i(getClass().toString(), "Called onDestroy");
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Navigate.up(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void onBackPressed() {
        if (DEBUG) Log.i(getClass().toString(), "Called calledOnBackPressed");
        super.onBackPressed();
        Navigate.back(this);
    }

    /**
     * Method to theme elements in the view hierarchy for this activity. By default, this method
     * sets the app's primary color, app icon, and background color. If the miniplayer is in the
     * hierarchy, it is also themed.
     */
    public void themeActivity() {
        Themes.updateColors(this);
        Themes.setApplicationIcon(this);

        if (findViewById(R.id.miniplayer) != null) {
            View miniplayer = (View) findViewById(R.id.miniplayer).getParent();

            miniplayer.setBackgroundColor(Themes.getBackgroundMiniplayer());
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void onUpdate() {
        if (DEBUG) Log.i(getClass().toString(), "Called onUpdate");
        updateMiniplayer();
    }

    @Override
    public void onError(String message) {
        if (DEBUG) Log.i(getClass().toString(), "Called onError : " + message);
        showSnackbar(message);
    }

    protected void showSnackbar(String message) {
        View content = findViewById(R.id.list);
        if (content == null) {
            content = findViewById(android.R.id.content);
        }
        Snackbar.make(content, message, Snackbar.LENGTH_LONG).show();
    }

    /**
     * Update the miniplayer to reflect the most recent @link PlayerService status. If no miniplayer
     * exists in the view, override this method with an instance_empty code block.
     */
    @SuppressWarnings("ResourceType")
    public void updateMiniplayer() {
        if (DEBUG) Log.i(getClass().toString(), "Called updateMiniplayer");
        final View miniplayerView = findViewById(R.id.miniplayer_holder);
        final Song nowPlaying = PlayerController.getNowPlaying();

        if (nowPlaying != null) {
            ImageView artworkImageView =
                    (ImageView) miniplayerView.findViewById(R.id.imageArtwork);
            TextView songTextView =
                    (TextView) miniplayerView.findViewById(R.id.textNowPlayingTitle);
            TextView artistTextView =
                    (TextView) miniplayerView.findViewById(R.id.textNowPlayingDetail);
            ImageView playButton =
                    (ImageView) miniplayerView.findViewById(R.id.playButton);

            if (PlayerController.getArtwork() != null) {
                artworkImageView.setImageBitmap(PlayerController.getArtwork());
            } else {
                artworkImageView.setImageResource(R.drawable.art_default);
            }

            songTextView.setText(nowPlaying.getSongName());
            artistTextView.setText(nowPlaying.getArtistName());
            playButton.setImageResource((PlayerController.isPlaying())
                    ? R.drawable.ic_pause_36dp
                    : R.drawable.ic_play_arrow_36dp);

        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.miniplayer:
                Navigate.to(this, NowPlayingActivity.class);
                break;
            case R.id.playButton:
                PlayerController.togglePlay();
                break;
            case R.id.skipButton:
                PlayerController.skip();
                break;
        }
    }

    @Nullable
    @SuppressWarnings("deprecation")
    public Drawable getDrawableCompat(@DrawableRes int id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return getDrawable(id);
        } else {
            return getResources().getDrawable(id);
        }
    }
}
