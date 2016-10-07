package com.dankideacentral.dic;

import android.Manifest;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.dankideacentral.dic.TweetListFragment.OnListFragmentInteractionListener;
import com.dankideacentral.dic.dummy.DummyContent;
import com.dankideacentral.dic.model.TweetNode;
import com.dankideacentral.dic.util.Fragmenter;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterItem;
import com.google.maps.android.clustering.ClusterManager;

import java.util.Arrays;

public class TweetFeedActivity extends BaseMapActivity
        implements OnListFragmentInteractionListener, ClusterManager.OnClusterClickListener, ClusterManager.OnClusterItemClickListener, LocationListener {
    private static final int PERMISSION_REQUEST_LOCATION = 0;
    private static final String CURRENT_FRAGMENT = "CURRENT_FRAGMENT";

    private static final int MIN_TIME = 250; //milliseconds
    private static final int MIN_DISTANCE = 0;

    private ClusterManager<TweetNode> clusterManager;

    private Fragment listFragment;
    private Fragmenter fm;

    private Button toggleButton;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_tweet_feed);
        fm = new Fragmenter(getSupportFragmentManager());

        listFragment = new TweetListFragment();

        fm.create(R.id.activity_tweet_feed, getFragment(), CURRENT_FRAGMENT);
        getFragment().getMapAsync(this);

        toggleButton = (Button) findViewById(R.id.toggle);
        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Fragment current = fm.find(CURRENT_FRAGMENT)
                        instanceof TweetListFragment
                            ? getFragment()
                            : listFragment;
                fm.create(R.id.activity_tweet_feed, current, CURRENT_FRAGMENT);
            }
        });
        requestPermission(Manifest.permission.ACCESS_FINE_LOCATION, PERMISSION_REQUEST_LOCATION);
    }

    private void requestPermission(String requestedPermission, int grantedPermission) {
        if (ContextCompat.checkSelfPermission(this, requestedPermission) != 0)
            requestPermissions(new String[]{requestedPermission}, grantedPermission);
    }


    @Override
    public void mapReady(GoogleMap map, LocationManager lm, final ClusterManager cm) {
        clusterManager = cm;

        clusterManager.setOnClusterClickListener(this);
        clusterManager.setOnClusterItemClickListener(this);

        try {
            lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    MIN_TIME,
                    MIN_DISTANCE,
                    this);
        } catch (SecurityException e) {
            Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onListFragmentInteraction(DummyContent.DummyItem item) {
        Snackbar.make(findViewById(R.id.activity_tweet_feed), item.toString(), Snackbar.LENGTH_LONG).show();
    }

    @Override
    public boolean onClusterClick(Cluster cluster) {
        Log.d("CLUSTER_CLICK", Arrays.toString(cluster.getItems().toArray()));
        return true;
    }
    @Override
    public boolean onClusterItemClick(ClusterItem clusterItem) {
        Log.d("CLUSTER_ITEM_CLICK", clusterItem.getPosition().toString());
        return false;
    }

    @Override
    public void onLocationChanged(Location loc) {
        LatLng currentLocation = new LatLng(loc.getLatitude(), loc.getLongitude());

        Log.d("LOCATION_LISTENER", currentLocation.toString());

        getMap().moveCamera(CameraUpdateFactory.newLatLng(currentLocation));

        clusterManager.addItem(new TweetNode(currentLocation));
        clusterManager.cluster();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}
}
