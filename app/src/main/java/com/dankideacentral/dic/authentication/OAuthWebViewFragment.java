package com.dankideacentral.dic.authentication;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.dankideacentral.dic.SearchActivity;
import com.dankideacentral.dic.R;
import com.dankideacentral.dic.TweetFeedActivity;

public class OAuthWebViewFragment extends Fragment {

    private static final String FINE_LOCATION_PERMISSION = "android.permissions.ACCESS_FINE_LOCATION";

    private WebView webView;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String auth = getArguments().getString(getString(R.string.string_extra_authentication_url));

        webView.loadUrl(auth);
        webView.setWebViewClient(new WebViewClient()
        {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.contains("oauth_verifier=")) {
                    Intent intent;

                    // Create user session
                    TwitterSession.getInstance().createSession(Uri.parse(url),
                            getActivity().getApplicationContext());

                    if (isLocationEnabled() == PackageManager.PERMISSION_GRANTED) {
                        intent = new Intent(getActivity().getApplicationContext(),
                                TweetFeedActivity.class);
                    } else {
                        intent = new Intent(getActivity().getApplicationContext(),
                                SearchActivity.class);
                    }

                    startActivity(intent);
                }

                view.loadUrl(url);
                return true;
            }
        });
        WebSettings webSettings = webView.getSettings();

        webSettings.setDomStorageEnabled(true);
        webSettings.setJavaScriptEnabled(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_oauth_webview,container,false);
        webView = (WebView) view.findViewById(R.id.webViewOAuth);
        return view;
    }

    /**
     * Checks the {@link PackageManager} to determine if location
     * services have been enabled.
     *
     * @return
     *          Either {@link PackageManager#PERMISSION_GRANTED} or
     *          {@link PackageManager#PERMISSION_DENIED}.
     */
    private int isLocationEnabled() {
        return getActivity().getPackageManager().checkPermission(
                FINE_LOCATION_PERMISSION, getActivity().getPackageName());
    }
}
