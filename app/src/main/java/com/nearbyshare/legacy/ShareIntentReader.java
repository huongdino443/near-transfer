package com.nearbyshare.legacy;

import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;

import java.util.ArrayList;

final class ShareIntentReader {
    private ShareIntentReader() {
    }

    static ArrayList<Uri> read(Intent intent) {
        ArrayList<Uri> result = new ArrayList<Uri>();
        if (intent == null) {
            return result;
        }

        String action = intent.getAction();
        boolean canShare = Intent.ACTION_SEND.equals(action) ||
                Intent.ACTION_SEND_MULTIPLE.equals(action);
        if (Intent.ACTION_SEND.equals(action)) {
            Parcelable stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (stream instanceof Uri) {
                addUnique(result, (Uri) stream);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<?> streams = intent.getParcelableArrayListExtra(
                    Intent.EXTRA_STREAM);
            if (streams != null) {
                for (Object stream : streams) {
                    if (stream instanceof Uri) {
                        addUnique(result, (Uri) stream);
                    }
                }
            }
        }

        if (canShare) {
            Uri data = intent.getData();
            if (data != null) {
                addUnique(result, data);
            }
        }
        return result;
    }

    static void addUnique(ArrayList<Uri> uris, Uri candidate) {
        if (candidate == null) {
            return;
        }
        for (Uri uri : uris) {
            if (uri.toString().equals(candidate.toString())) {
                return;
            }
        }
        uris.add(candidate);
    }
}