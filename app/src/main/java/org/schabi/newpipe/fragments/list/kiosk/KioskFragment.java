/*
 * SPDX-FileCopyrightText: 2017-2024 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.kiosk;

import android.os.Bundle;
import android.text.TextUtils;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.preference.PreferenceManager;

import com.evernote.android.state.State;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.util.ContentBlockingHelper;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.Localization;

import java.util.List;

import io.reactivex.rxjava3.core.Single;

public class KioskFragment extends BaseListInfoFragment<StreamInfoItem, KioskInfo> {
    @State
    String kioskId = "";
    String kioskTranslatedName;
    @State
    ContentCountry contentCountry;

    private DiscoveryPersonalization personalization;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    public static KioskFragment getInstance(final int serviceId) throws ExtractionException {
        return getInstance(serviceId, NewPipe.getService(serviceId)
                .getKioskList().getDefaultKioskId());
    }

    public static KioskFragment getInstance(final int serviceId, final String kioskId)
            throws ExtractionException {
        final KioskFragment instance = new KioskFragment();
        final StreamingService service = NewPipe.getService(serviceId);
        final ListLinkHandlerFactory kioskLinkHandlerFactory = service.getKioskList()
                .getListLinkHandlerFactoryByType(kioskId);
        instance.setInitialData(serviceId,
                kioskLinkHandlerFactory.fromId(kioskId).getUrl(), kioskId);
        instance.kioskId = kioskId;
        return instance;
    }

    public KioskFragment() {
        super(UserAction.REQUESTED_KIOSK);
    }

    @NonNull
    @Override
    protected ContentBlockingHelper.Target getContentBlockingTarget() {
        return ContentBlockingHelper.Target.KIOSKS;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        kioskTranslatedName = KioskTranslator.getTranslatedKioskName(kioskId, activity);
        name = kioskTranslatedName;
        contentCountry = Localization.getPreferredContentCountry(requireContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!Localization.getPreferredContentCountry(requireContext()).equals(contentCountry)
                || (currentInfo != null && personalization == null && !isLoading.get())) {
            reloadContent();
        }
        if (useAsFrontPage && activity != null) {
            try {
                setTitle(kioskTranslatedName);
            } catch (final Exception e) {
                showSnackBarError(new ErrorInfo(e, UserAction.UI_ERROR, "Setting kiosk title"));
            }
        }
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.fragment_kiosk, container, false);
        if ("youtube_shorts".equals(kioskId)
                && PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getBoolean("materialtube_halloween_theme", false)) {
            addHalloweenPumpkin(root);
        }
        return root;
    }

    private void addHalloweenPumpkin(@NonNull final View root) {
        final RelativeLayout layout = (RelativeLayout) root;
        final TextView pumpkin = new TextView(requireContext());
        pumpkin.setId(View.generateViewId());
        pumpkin.setText("🎃");
        pumpkin.setTextSize(30);
        pumpkin.setGravity(android.view.Gravity.CENTER);
        pumpkin.setContentDescription(getString(R.string.materialtube_halloween_easter_egg));

        final RelativeLayout.LayoutParams pumpkinParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (52
                * getResources().getDisplayMetrics().density));
        pumpkinParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        layout.addView(pumpkin, pumpkinParams);

        final View items = root.findViewById(R.id.items_list);
        if (items != null) {
            final RelativeLayout.LayoutParams listParams =
                    (RelativeLayout.LayoutParams) items.getLayoutParams();
            listParams.addRule(RelativeLayout.ABOVE, pumpkin.getId());
            items.setLayoutParams(listParams);
        }

        pumpkin.setOnClickListener(view -> {
            final ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 450);
            pumpkin.postDelayed(tone::release, 500);
            Toast.makeText(requireContext(), R.string.materialtube_halloween_easter_egg,
                    Toast.LENGTH_LONG).show();
        });
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (!TextUtils.isEmpty(kioskId)) {
            menu.add(R.string.discovery_personalize).setOnMenuItemClickListener(item -> {
                DiscoveryPersonalization.configure(requireContext(), this::reloadContent);
                return true;
            });
        }
        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null && useAsFrontPage) {
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and handle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public Single<KioskInfo> loadResult(final boolean forceReload) {
        contentCountry = Localization.getPreferredContentCountry(requireContext());
        final android.content.Context context = requireContext().getApplicationContext();
        return ExtractorHelper.getKioskInfo(serviceId, url, forceReload)
                .doOnSuccess(info -> personalization = DiscoveryPersonalization.load(context));
    }

    @Override
    public Single<ListExtractor.InfoItemsPage<StreamInfoItem>> loadMoreItemsLogic() {
        final android.content.Context context = requireContext().getApplicationContext();
        return ExtractorHelper.getMoreKioskItems(serviceId, url, currentNextPage)
                .doOnSuccess(info -> personalization = DiscoveryPersonalization.load(context));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected List<StreamInfoItem> getDisplayItems(final List<StreamInfoItem> items) {
        return personalization == null ? items : personalization.apply(items);
    }

    @Override
    public void handleResult(@NonNull final KioskInfo result) {
        super.handleResult(result);

        name = kioskTranslatedName;
        setTitle(kioskTranslatedName);
    }

    @Override
    public void showEmptyState() {
        // show "no live streams" for live stream kiosk
        super.showEmptyState();
        if (currentInfo != null && "live".equals(currentInfo.getId())
                && ServiceList.MediaCCC.getServiceId() == currentInfo.getServiceId()) {
            setEmptyStateMessage(R.string.no_live_streams);
        }
    }
}
