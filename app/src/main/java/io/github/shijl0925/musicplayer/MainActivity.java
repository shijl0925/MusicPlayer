package io.github.shijl0925.musicplayer;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int REQUEST_AUDIO = 7;
    private final List<Track> tracks = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout content;
    private LinearLayout tabs;
    private LinearLayout miniPlayer;
    private TextView miniTitle;
    private TextView miniArtist;
    private ImageView miniArt;
    private ImageButton miniPlay;
    private MediaPlayer player;
    private int currentIndex = -1;
    private boolean userSeeking;
    private SeekBar activeSeek;
    private TextView activeElapsed;
    private TextView activeDuration;
    private final Runnable progressTick = new Runnable() {
        @Override public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.rgb(244, 244, 244));
        buildShell();
        if (hasAudioPermission()) loadLibrary(); else showPermissionScreen();
    }

    private void buildShell() {
        FrameLayout root = new FrameLayout(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(248, 245, 248));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(this);
        title.setText("Music");
        title.setTextColor(Color.rgb(28, 28, 32));
        title.setTextSize(34);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(20), dp(18), dp(20), dp(4));
        page.addView(title);

        tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(16), 0, dp(16), dp(10));
        page.addView(tabs);

        content = new FrameLayout(this);
        page.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void buildTabs(String selected) {
        tabs.removeAllViews();
        addTab("Albums", selected.equals("Albums"), () -> showAlbums());
        addTab("Play list", selected.equals("Play list"), () -> showPlaylist());
        addTab("Now playing", selected.equals("Now playing"), () -> showPlayer());
    }

    private void addTab(String label, boolean selected, final Runnable action) {
        TextView tab = new TextView(this);
        tab.setText(label);
        tab.setTextSize(15);
        tab.setTypeface(Typeface.DEFAULT_BOLD);
        tab.setTextColor(selected ? Color.WHITE : Color.rgb(76, 76, 82));
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(dp(18), dp(9), dp(18), dp(9));
        GradientDrawable bg = round(selected ? Color.rgb(18, 18, 22) : Color.WHITE, dp(20));
        tab.setBackground(bg);
        tab.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, dp(10), 0);
        tabs.addView(tab, lp);
    }

    private void showPermissionScreen() {
        buildTabs("Albums");
        content.removeAllViews();
        LinearLayout empty = centeredColumn();
        TextView icon = new TextView(this);
        icon.setText("♪");
        icon.setTextSize(72);
        icon.setTextColor(Color.rgb(229, 57, 53));
        icon.setGravity(Gravity.CENTER);
        TextView message = titleText("Allow access to local music", 22);
        TextView details = bodyText("MusicPlayer scans only audio files stored on this device.");
        details.setGravity(Gravity.CENTER);
        Button allow = new Button(this);
        allow.setText("Grant audio permission");
        allow.setOnClickListener(v -> requestPermissions(new String[]{audioPermission()}, REQUEST_AUDIO));
        empty.addView(icon);
        empty.addView(message);
        empty.addView(details);
        empty.addView(allow);
        content.addView(empty, new FrameLayout.LayoutParams(-1, -1));
    }

    private void loadLibrary() {
        tracks.clear();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        try (Cursor cursor = getContentResolver().query(collection, projection, selection, null,
                MediaStore.Audio.Media.ALBUM + " COLLATE NOCASE ASC, " + MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    long albumId = cursor.getLong(albumIdCol);
                    tracks.add(new Track(
                            safe(cursor.getString(titleCol), "Unknown track"),
                            safe(cursor.getString(artistCol), "Unknown artist"),
                            safe(cursor.getString(albumCol), "Unknown album"),
                            ContentUris.withAppendedId(collection, id),
                            ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId),
                            cursor.getLong(durationCol)));
                }
            }
        } catch (SecurityException ex) {
            showPermissionScreen();
            return;
        }
        if (tracks.isEmpty()) showEmptyLibrary(); else showAlbums();
    }

    private void showEmptyLibrary() {
        buildTabs("Albums");
        content.removeAllViews();
        LinearLayout empty = centeredColumn();
        TextView note = titleText("No local music found", 24);
        TextView details = bodyText("Copy MP3, M4A, WAV, or other audio files onto this device and reopen the app.");
        details.setGravity(Gravity.CENTER);
        empty.addView(note);
        empty.addView(details);
        content.addView(empty, new FrameLayout.LayoutParams(-1, -1));
    }

    private void showAlbums() {
        buildTabs("Albums");
        content.removeAllViews();
        FrameLayout frame = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout vertical = new LinearLayout(this);
        vertical.setOrientation(LinearLayout.VERTICAL);
        vertical.setPadding(dp(16), 0, dp(16), dp(106));
        TextView section = bodyText("Favorites");
        section.setTextColor(Color.rgb(40, 40, 45));
        section.setTextSize(16);
        vertical.addView(section);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        vertical.addView(grid);

        if (!tracks.isEmpty()) addAlbumCard(grid, "Favorite Tracks", trackCountLabel(tracks.size()), tracks.get(0), -1);
        for (Album album : albums()) addAlbumCard(grid, album.name, album.artist, album.firstTrack, album.firstIndex);
        scroll.addView(vertical);
        frame.addView(scroll);
        attachMiniPlayer(frame);
        content.addView(frame);
    }

    private void addAlbumCard(GridLayout grid, String name, String subtitle, Track artTrack, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(4), dp(8), dp(4), dp(10));
        ImageView art = coverView(dp(146), artTrack, true);
        TextView title = titleText(name, 17);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView sub = bodyText(subtitle);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(art);
        card.addView(title);
        card.addView(sub);
        card.setOnClickListener(v -> play(index >= 0 ? index : 0));
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = (getResources().getDisplayMetrics().widthPixels - dp(48)) / 2;
        lp.setMargins(0, 0, dp(8), dp(2));
        grid.addView(card, lp);
    }

    private void showPlaylist() {
        buildTabs("Play list");
        content.removeAllViews();
        FrameLayout frame = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), 0, dp(16), dp(106));
        TextView header = titleText("Local Tracks", 24);
        list.addView(header);
        for (int i = 0; i < tracks.size(); i++) list.addView(songRow(i, true));
        scroll.addView(list);
        frame.addView(scroll);
        attachMiniPlayer(frame);
        content.addView(frame);
    }

    private View songRow(int index, boolean divider) {
        Track track = tracks.get(index);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        ImageView art = coverView(dp(58), track, false);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, dp(8), 0);
        TextView title = titleText(track.title, 17);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView sub = bodyText(track.artist + " • " + track.album);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);
        texts.addView(sub);
        TextView duration = bodyText(format(track.duration));
        row.addView(art);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(duration);
        row.setOnClickListener(v -> play(index));
        if (!divider) return row;
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(row);
        View line = new View(this);
        line.setBackgroundColor(Color.rgb(232, 228, 232));
        wrap.addView(line, new LinearLayout.LayoutParams(-1, 1));
        return wrap;
    }

    private void showPlayer() {
        buildTabs("Now playing");
        content.removeAllViews();
        if (tracks.isEmpty()) {
            showEmptyLibrary();
            return;
        }
        if (currentIndex < 0) currentIndex = 0;
        Track track = tracks.get(currentIndex);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(24), dp(12), dp(24), dp(22));
        content.addView(page, new FrameLayout.LayoutParams(-1, -1));

        ImageView art = coverView(Math.min(getResources().getDisplayMetrics().widthPixels - dp(72), dp(320)), track, true);
        page.addView(art);
        TextView title = titleText(track.title, 28);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView artist = bodyText(track.artist + " • " + track.album);
        artist.setGravity(Gravity.CENTER);
        artist.setSingleLine(true);
        artist.setEllipsize(TextUtils.TruncateAt.END);
        page.addView(title, new LinearLayout.LayoutParams(-1, -2));
        page.addView(artist, new LinearLayout.LayoutParams(-1, -2));

        Space space = new Space(this);
        page.addView(space, new LinearLayout.LayoutParams(1, dp(24)));
        activeSeek = new SeekBar(this);
        activeSeek.setMax((int)Math.max(track.duration, 1));
        activeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (player != null) player.seekTo(seekBar.getProgress());
                userSeeking = false;
                updateProgress();
            }
        });
        page.addView(activeSeek, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        activeElapsed = bodyText("0:00");
        activeDuration = bodyText(format(track.duration));
        times.addView(activeElapsed);
        times.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1));
        times.addView(activeDuration);
        page.addView(times, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(22), 0, 0);
        ImageButton prev = controlButton("‹‹");
        ImageButton play = controlButton(isPlaying() ? "❚❚" : "▶");
        ImageButton next = controlButton("››");
        prev.setOnClickListener(v -> skip(-1));
        play.setOnClickListener(v -> togglePlay());
        next.setOnClickListener(v -> skip(1));
        controls.addView(prev);
        controls.addView(play);
        controls.addView(next);
        page.addView(controls);
        updateProgress();
    }

    private void attachMiniPlayer(FrameLayout frame) {
        if (tracks.isEmpty()) return;
        int index = currentIndex >= 0 ? currentIndex : 0;
        Track track = tracks.get(index);
        miniPlayer = new LinearLayout(this);
        miniPlayer.setGravity(Gravity.CENTER_VERTICAL);
        miniPlayer.setPadding(dp(10), dp(8), dp(10), dp(8));
        miniPlayer.setBackground(round(Color.WHITE, dp(28)));
        miniArt = coverView(dp(48), track, false);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(10), 0, dp(6), 0);
        miniTitle = titleText(track.title, 16);
        miniTitle.setSingleLine(true);
        miniTitle.setEllipsize(TextUtils.TruncateAt.END);
        miniArtist = bodyText(track.artist);
        miniArtist.setSingleLine(true);
        miniArtist.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(miniTitle);
        texts.addView(miniArtist);
        miniPlay = controlButton(isPlaying() ? "❚❚" : "▶");
        ImageButton queue = controlButton("☰");
        miniPlay.setOnClickListener(v -> togglePlay());
        queue.setOnClickListener(v -> showPlaylist());
        miniPlayer.setOnClickListener(v -> showPlayer());
        miniPlayer.addView(miniArt);
        miniPlayer.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        miniPlayer.addView(miniPlay);
        miniPlayer.addView(queue);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.setMargins(dp(16), 0, dp(16), dp(16));
        frame.addView(miniPlayer, lp);
    }

    private void play(int index) {
        if (index < 0 || index >= tracks.size()) return;
        currentIndex = index;
        Track track = tracks.get(index);
        releasePlayer();
        player = new MediaPlayer();
        try {
            player.setDataSource(this, track.uri);
            player.setOnCompletionListener(mp -> skip(1));
            player.prepare();
            player.start();
            handler.removeCallbacks(progressTick);
            handler.post(progressTick);
            refreshMini();
            showPlayer();
        } catch (IOException | RuntimeException ex) {
            Toast.makeText(this, "Unable to play this track", Toast.LENGTH_SHORT).show();
            releasePlayer();
        }
    }

    private void togglePlay() {
        if (tracks.isEmpty()) return;
        if (player == null) {
            play(currentIndex >= 0 ? currentIndex : 0);
            return;
        }
        if (player.isPlaying()) player.pause(); else player.start();
        refreshMini();
        showPlayer();
    }

    private void skip(int delta) {
        if (tracks.isEmpty()) return;
        int next = currentIndex < 0 ? 0 : (currentIndex + delta + tracks.size()) % tracks.size();
        play(next);
    }

    private void refreshMini() {
        if (currentIndex < 0 || currentIndex >= tracks.size()) return;
        Track track = tracks.get(currentIndex);
        if (miniTitle != null) miniTitle.setText(track.title);
        if (miniArtist != null) miniArtist.setText(track.artist);
        if (miniArt != null) setArtwork(miniArt, track);
        if (miniPlay != null) miniPlay.setImageDrawable(new TextDrawable(isPlaying() ? "❚❚" : "▶"));
    }

    private void updateProgress() {
        if (player == null || activeSeek == null) return;
        int duration = player.getDuration();
        int position = player.getCurrentPosition();
        activeSeek.setMax(Math.max(duration, 1));
        if (!userSeeking) activeSeek.setProgress(position);
        if (activeElapsed != null) activeElapsed.setText(format(position));
        if (activeDuration != null) activeDuration.setText(format(duration));
    }

    private List<Album> albums() {
        Map<String, Album> map = new LinkedHashMap<>();
        for (int i = 0; i < tracks.size(); i++) {
            Track t = tracks.get(i);
            String key = t.album + "\n" + t.artist;
            if (!map.containsKey(key)) map.put(key, new Album(t.album, t.artist, t, i));
        }
        return new ArrayList<>(map.values());
    }

    private ImageView coverView(int size, Track track, boolean large) {
        ImageView view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setClipToOutline(true);
        view.setBackground(round(Color.rgb(230, 230, 233), large ? dp(18) : dp(24)));
        setArtwork(view, track);
        view.setLayoutParams(new ViewGroup.LayoutParams(size, size));
        return view;
    }

    private void setArtwork(ImageView view, Track track) {
        view.setImageURI(null);
        if (track != null) view.setImageURI(track.artUri);
        if (view.getDrawable() == null) view.setImageDrawable(placeholder(track == null ? 0 : track.album.hashCode()));
    }

    private GradientDrawable placeholder(int seed) {
        int[] palette = {Color.rgb(225, 57, 93), Color.rgb(92, 78, 220), Color.rgb(255, 173, 51), Color.rgb(100, 214, 180), Color.rgb(247, 142, 184)};
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{palette[Math.abs(seed) % palette.length], palette[Math.abs(seed / 7 + 2) % palette.length]});
    }

    private ImageButton controlButton(String text) {
        ImageButton button = new ImageButton(this);
        button.setBackground(round(Color.WHITE, dp(28)));
        button.setContentDescription(text);
        button.setImageDrawable(new TextDrawable(text));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(56), dp(56));
        lp.setMargins(dp(7), 0, dp(7), 0);
        button.setLayoutParams(lp);
        return button;
    }

    private LinearLayout centeredColumn() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(dp(28), dp(28), dp(28), dp(28));
        return layout;
    }

    private TextView titleText(String value, int sp) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.rgb(30, 30, 35));
        return view;
    }

    private TextView bodyText(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(14);
        view.setTextColor(Color.rgb(102, 101, 108));
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private String format(long millis) {
        long total = Math.max(0, millis / 1000);
        return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60);
    }

    private String trackCountLabel(int count) {
        return count + (count == 1 ? " Track" : " Tracks");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String safe(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private boolean hasAudioPermission() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    private String audioPermission() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) loadLibrary();
        else showPermissionScreen();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
    }

    private void releasePlayer() {
        handler.removeCallbacks(progressTick);
        if (player != null) {
            player.release();
            player = null;
        }
    }

    private boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    private static final class Track {
        final String title;
        final String artist;
        final String album;
        final Uri uri;
        final Uri artUri;
        final long duration;
        Track(String title, String artist, String album, Uri uri, Uri artUri, long duration) {
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.uri = uri;
            this.artUri = artUri;
            this.duration = duration;
        }
    }

    private static final class Album {
        final String name;
        final String artist;
        final Track firstTrack;
        final int firstIndex;
        Album(String name, String artist, Track firstTrack, int firstIndex) {
            this.name = name;
            this.artist = artist;
            this.firstTrack = firstTrack;
            this.firstIndex = firstIndex;
        }
    }

    private static final class TextDrawable extends Drawable {
        private final String text;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        TextDrawable(String text) {
            this.text = text;
            paint.setColor(Color.rgb(28, 28, 32));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
        }

        @Override public void draw(Canvas canvas) {
            paint.setTextSize(getBounds().height() * 0.42f);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float x = getBounds().centerX();
            float y = getBounds().centerY() - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(text, x, y, paint);
        }

        @Override public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
