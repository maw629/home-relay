package app.maw629.homerelay.share;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SampleContentProvider extends ContentProvider {
    public static final String REPORT_NAME = "report.pdf";
    private static final byte[] REPORT_BYTES = {'h', 'e', 'l', 'l', 'o'};
    private static final Set<String> READABLE_PATHS = new HashSet<>(Arrays.asList(
            "report.pdf",
            "opaque-id",
            "missing-display-name",
            "failed-stream.pdf",
            "blocking-report.pdf"
    ));

    private static volatile BlockingControl blockingControl = new BlockingControl();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder
    ) {
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME});
        cursor.addRow(new Object[]{
                "missing-display-name".equals(uri.getLastPathSegment()) ? null : REPORT_NAME
        });
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "application/pdf";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Read-only test provider");
        }

        String path = uri.getLastPathSegment();
        if (!READABLE_PATHS.contains(path)) {
            throw new FileNotFoundException(uri.toString());
        }

        ParcelFileDescriptor[] pipe;
        try {
            pipe = "failed-stream.pdf".equals(path)
                    ? ParcelFileDescriptor.createReliablePipe()
                    : ParcelFileDescriptor.createPipe();
        } catch (IOException exception) {
            FileNotFoundException failure = new FileNotFoundException(uri.toString());
            failure.initCause(exception);
            throw failure;
        }
        ParcelFileDescriptor writeSide = pipe[1];
        if ("failed-stream.pdf".equals(path)) {
            try {
                writeSide.closeWithError("Test source stream failed");
            } catch (IOException exception) {
                FileNotFoundException failure = new FileNotFoundException(uri.toString());
                failure.initCause(exception);
                throw failure;
            }
            return pipe[0];
        }

        BlockingControl control = blockingControl;
        Thread writer = new Thread(() -> writeSource(path, writeSide, control), "SampleContentProvider");
        writer.setDaemon(true);
        writer.start();
        return pipe[0];
    }

    private static void writeSource(
            String path,
            ParcelFileDescriptor writeSide,
            BlockingControl control
    ) {
        try (ParcelFileDescriptor.AutoCloseOutputStream output =
                     new ParcelFileDescriptor.AutoCloseOutputStream(writeSide)) {
            if ("blocking-report.pdf".equals(path)) {
                control.started.countDown();
                control.release.await();
            }
            output.write(REPORT_BYTES);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
            // The client may close its pipe while this fixture is writing.
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    public static void resetBlockingSource() {
        blockingControl = new BlockingControl();
    }

    public static boolean awaitBlockingSourceStarted(long timeout, TimeUnit unit)
            throws InterruptedException {
        return blockingControl.started.await(timeout, unit);
    }

    public static void releaseBlockingSource() {
        blockingControl.release.countDown();
    }

    private static final class BlockingControl {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
    }
}
