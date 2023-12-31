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

package com.android.settings.network;

import static com.android.settings.Utils.SETTINGS_PACKAGE_NAME;

import static androidx.lifecycle.Lifecycle.Event.ON_PAUSE;
import static androidx.lifecycle.Lifecycle.Event.ON_RESUME;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.annotations.VisibleForTesting;
import com.android.settings.R;
import com.android.settings.network.telephony.MobileNetworkUtils;
import com.android.settingslib.core.AbstractPreferenceController;

import java.util.List;
import java.util.Map;

/**
 * This populates the entries on a page which lists all available mobile subscriptions. Each entry
 * has the name of the subscription with some subtext giving additional detail, and clicking on the
 * entry brings you to a details page for that network.
 *
 * @deprecated This class will be removed in Android U, use
 * {@link NetworkProviderSimsCategoryController} and
 * {@link NetworkProviderDownloadedSimsCategoryController} instead.
 */
@Deprecated
public class MobileNetworkListController extends AbstractPreferenceController implements
        LifecycleObserver, SubscriptionsChangeListener.SubscriptionsChangeListenerClient {
    private static final String TAG = "MobileNetworkListCtlr";

    @VisibleForTesting
    static final String KEY_ADD_MORE = "add_more";

    private SubscriptionManager mSubscriptionManager;
    private SubscriptionsChangeListener mChangeListener;
    private PreferenceScreen mPreferenceScreen;
    private Map<Integer, Preference> mPreferences;

    public MobileNetworkListController(Context context, Lifecycle lifecycle) {
        super(context);
        mSubscriptionManager = context.getSystemService(SubscriptionManager.class);
        mChangeListener = new SubscriptionsChangeListener(context, this);
        mPreferences = new ArrayMap<>();
        lifecycle.addObserver(this);
    }

    @OnLifecycleEvent(ON_RESUME)
    public void onResume() {
        mChangeListener.start();
        update();
    }

    @OnLifecycleEvent(ON_PAUSE)
    public void onPause() {
        mChangeListener.stop();
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreferenceScreen = screen;
        mPreferenceScreen.findPreference(KEY_ADD_MORE).setVisible(
                MobileNetworkUtils.showEuiccSettings(mContext));
        update();
    }

    private void update() {
        if (mPreferenceScreen == null) {
            return;
        }

        // Since we may already have created some preferences previously, we first grab the list of
        // those, then go through the current available subscriptions making sure they are all
        // present in the screen, and finally remove any now-outdated ones.
        final Map<Integer, Preference> existingPreferences = mPreferences;
        mPreferences = new ArrayMap<>();

        final List<SubscriptionInfo> subscriptions = SubscriptionUtil.getAvailableSubscriptions(
                mContext);
        for (SubscriptionInfo info : subscriptions) {
            final int subId = info.getSubscriptionId();
            Preference pref = existingPreferences.remove(subId);
            if (pref == null) {
                pref = new Preference(mPreferenceScreen.getContext());
                mPreferenceScreen.addPreference(pref);
            }
            final CharSequence displayName = SubscriptionUtil.getUniqueSubscriptionDisplayName(
                    info, mContext);
            pref.setTitle(displayName);

            if (info.isEmbedded()) {
                if (mSubscriptionManager.isActiveSubscriptionId(subId)) {
                    pref.setSummary(R.string.mobile_network_active_esim);
                } else {
                    pref.setSummary(R.string.mobile_network_inactive_esim);
                }
            } else {
                int slotId = mSubscriptionManager.getPhoneId(subId);
                if (mSubscriptionManager.isActiveSubscriptionId(subId) &&
                        TelephonyManager.getSimStateForSlotIndex(slotId) !=
                                TelephonyManager.SIM_STATE_NOT_READY) {
                    pref.setSummary(R.string.mobile_network_active_sim);
                } else if (SubscriptionUtil.showToggleForPhysicalSim(mSubscriptionManager)) {
                    pref.setSummary(mContext.getString(R.string.mobile_network_inactive_sim));
                } else {
                    pref.setSummary(mContext.getString(R.string.mobile_network_tap_to_activate,
                            displayName));
                }
            }

            pref.setOnPreferenceClickListener(clickedPref -> {
                if (!info.isEmbedded() && !mSubscriptionManager.isActiveSubscriptionId(subId)
                        && !SubscriptionUtil.showToggleForPhysicalSim(mSubscriptionManager)) {
                    SubscriptionUtil.startToggleSubscriptionDialogActivity(mContext, subId, true);
                } else {
                    final Intent intent = new Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS);
                    intent.setPackage(SETTINGS_PACKAGE_NAME);
                    intent.putExtra(Settings.EXTRA_SUB_ID, info.getSubscriptionId());
                    mContext.startActivity(intent);
                }
                return true;
            });
            mPreferences.put(subId, pref);
        }
        for (Preference pref : existingPreferences.values()) {
            mPreferenceScreen.removePreference(pref);
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getPreferenceKey() {
        return null;
    }

    @Override
    public void onAirplaneModeChanged(boolean airplaneModeEnabled) {
    }

    @Override
    public void onSubscriptionsChanged() {
        update();
    }
}
