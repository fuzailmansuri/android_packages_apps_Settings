/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.wifi.savedaccesspoints2;

import android.content.Context;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.android.settings.core.BasePreferenceController;
import com.android.settings.wifi.WifiEntryPreference;
import com.android.wifitrackerlib.WifiEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller that manages a PreferenceGroup, which contains a list of saved access points.
 */
public class SavedAccessPointsPreferenceController2 extends BasePreferenceController implements
        Preference.OnPreferenceClickListener {

    private PreferenceGroup mPreferenceGroup;
    private SavedAccessPointsWifiSettings2 mHost;
    @VisibleForTesting
    List<WifiEntry> mWifiEntries = new ArrayList<>();

    public SavedAccessPointsPreferenceController2(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    /**
     * Set {@link SavedAccessPointsWifiSettings2} for click callback action.
     */
    public SavedAccessPointsPreferenceController2 setHost(SavedAccessPointsWifiSettings2 host) {
        mHost = host;
        return this;
    }

    @Override
    public int getAvailabilityStatus() {
        return mWifiEntries.size() > 0 ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        mPreferenceGroup = screen.findPreference(getPreferenceKey());
        updatePreference();
        super.displayPreference(screen);
    }

    @VisibleForTesting
    void displayPreference(PreferenceScreen screen, List<WifiEntry> wifiEntries) {
        if (wifiEntries == null || wifiEntries.isEmpty()) {
            mWifiEntries.clear();
        } else {
            mWifiEntries = wifiEntries;
        }

        displayPreference(screen);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (mHost != null) {
            mHost.showWifiPage(preference.getKey(), preference.getTitle());
        }
        return false;
    }

    /**
     * mPreferenceGroup is not in a RecyclerView. To keep TalkBack focus, this method should not
     * mPreferenceGroup.removeAll() then mPreferenceGroup.addPreference for mWifiEntries.
     */
    private void updatePreference() {
        // Build key→entry map once (O(M)) to avoid O(N×M) getKey()/JSON-serialization per pref.
        // calling WifiEntry.getKey() inside a stream filter per existing preference
        // caused ANR under scan-result bursts with many saved networks.
        Map<String, WifiEntry> keyToEntry = new LinkedHashMap<>();
        for (WifiEntry entry : mWifiEntries) {
            keyToEntry.put(entry.getKey(), entry);
        }

        // Update WifiEntry to existing preference and find out which WifiEntry was removed by key.
        List<String> removedKeys = new ArrayList<>();
        int preferenceCount = mPreferenceGroup.getPreferenceCount();
        for (int i = 0; i < preferenceCount; i++) {
            WifiEntryPreference pref = (WifiEntryPreference) mPreferenceGroup.getPreference(i);
            WifiEntry wifiEntry = keyToEntry.get(pref.getKey());
            if (wifiEntry != null) {
                pref.setWifiEntry(wifiEntry);
            } else {
                removedKeys.add(pref.getKey());
            }
        }
        // Remove preference by WifiEntry's key.
        for (String removedKey : removedKeys) {
            mPreferenceGroup.removePreference(mPreferenceGroup.findPreference(removedKey));
        }

        // Add the Preference of new added WifiEntry.
        for (String key : keyToEntry.keySet()) {
            if (mPreferenceGroup.findPreference(key) == null) {
                WifiEntry wifiEntry = keyToEntry.get(key);
                WifiEntryPreference preference = new WifiEntryPreference(mContext, wifiEntry);
                preference.setKey(key);
                preference.setOnPreferenceClickListener(this);
                mPreferenceGroup.addPreference(preference);
            }
        }
    }
}
