package com.dankideacentral.dic.activities;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;

import android.support.v4.app.FragmentTransaction;

import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.app.NotificationCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.dankideacentral.dic.R;
import com.dankideacentral.dic.fragments.TweetListFragment;
import com.dankideacentral.dic.fragments.TweetListFragment.OnListFragmentInteractionListener;
import com.dankideacentral.dic.data.TwitterStreamService;
import com.dankideacentral.dic.util.TwitterUtil;
import com.dankideacentral.dic.authentication.TwitterSession;
import com.dankideacentral.dic.model.TweetNode;
import com.dankideacentral.dic.util.Fragmenter;
import com.dankideacentral.dic.util.ImageProcessor;
import com.dankideacentral.dic.util.LocationFinder;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterItem;
import com.google.maps.android.clustering.ClusterManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import twitter4j.IDs;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;

public class TweetFeedActivity extends BaseMapActivity
        implements OnListFragmentInteractionListener, ClusterManager.OnClusterClickListener, ClusterManager.OnClusterItemClickListener {

    private static final int MAP_ZOOM_DISTANCE = 12;
    private static final String CURRENT_FRAGMENT = "CURRENT_FRAGMENT";
    private static final String LOG_TAG = "TweetFeedActivity";

    private LatLng currentLocation;
    private ClusterManager<TweetNode> clusterManager;

    private Fragmenter fm;
    private LocationFinder locationFinder;
    private Twitter twitter;

    private Set<Long> friends = new HashSet<>();
    private Set<Long> followers = new HashSet<>();
    private static int notificationId = 0;

    @Override
    public void onStart () {
        super.onStart();
        Log.v(getClass().getName(), "onStart");
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(getClass().getName(), "onCreate");

        setContentView(R.layout.activity_tweet_feed);
        fm = new Fragmenter(getSupportFragmentManager());
        twitter = TwitterUtil.getInstance().getTwitter();

        // Asynchronously fetch the user's friends and followers
        new FetchTwitterFriends().execute();
        new FetchTwitterFollowers().execute();

        // Set the navigation icon of the tool bar & its onClick listener
        setToolbar();

        fm.create(R.id.layout_tweet_feed, getFragment(), CURRENT_FRAGMENT);
        getFragment().getMapAsync(this);
    }

    public void initCurrentLocation() {
        Log.v(getClass().getName(), "initCurrentLocation");

        currentLocation = getIntent().getParcelableExtra(getString(
                R.string.search_location_key));
        // TODO: For demo, since unable to retrieve current loc, just redirect to Search
        if (currentLocation == null) {
            startActivity(new Intent(this, SearchActivity.class));
        }
        // Case LatLng object returned is null (Could mean activity loaded on startup)
        if (currentLocation == null) {
            Log.v(getClass().getName(), "current loc was null");

            getCurrentLocation(new Handler.Callback() {
                @Override
                public boolean handleMessage(Message msg) {

                    Bundle args = msg.getData();
                    double lat = args.getDouble("lat");
                    double lon = args.getDouble("lon");
                    currentLocation = new LatLng(lat, lon);
                    Log.v(getClass().getName(), "currentLocation = " + currentLocation.toString());

                    animateCamera(currentLocation);
                    startTwitterStreamService(currentLocation);
                    return true;
                }
            });
        } else {
            // Start and bind the tweet stream service
            startTwitterStreamService(currentLocation);
            animateCamera(currentLocation);
        }
    }

    public void animateCamera(LatLng loc) {
        getMap().animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, MAP_ZOOM_DISTANCE));
    }

    @Override
    public void mapReady(GoogleMap map, LocationManager lm, final ClusterManager cm) {
        // set up broadcast receiver
        Log.v(getClass().getName(), "mapReady called");
        initCurrentLocation();
        LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Status tweet = (Status) intent.getExtras().get("tweet");
                final TweetNode mNode = new TweetNode(tweet);
                mNode.processImage(new Handler.Callback() {
                    @Override
                    public boolean handleMessage(Message msg) {
                        try {
                            clusterManager.addItem(mNode);
                            clusterManager.cluster();
                        } catch (Exception e) {
                            e.printStackTrace();
                            return false;
                        }
                        return true;
                    }
                });
                Log.v("Received Tweet: ", tweet.toString());

                // Send notification if tweeted by a friend or follower
                String tweetUserName = tweet.getUser().getName();
                long tweetUserId = tweet.getUser().getId();
                if(friends.contains(tweetUserId)) {
                    sendNotification("Your friend " + tweetUserName + " tweeted near you!");
                } else if(followers.contains(tweetUserId)) {
                    sendNotification("Your follower " + tweetUserName + " tweeted near you!");
                } else if(tweet.getUser().isVerified()) { // For testing purposes
                    sendNotification("Celebrity Tweeter " + tweetUserName + " tweeted near you!");
                }
            }
        }, new IntentFilter(getString(R.string.tweet_broadcast)));

        clusterManager = cm;
    }

    private void sendNotification(String message) {
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_cloud_white_24dp)
                        .setContentTitle("Migrate")
                        .setContentText(message);

        // Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(this, TweetFeedActivity.class);

        // The stack builder object will contain an artificial back stack for the started Activity.
        // This ensures that navigating backward from the Activity leads out of application to the
        // Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);

        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(TweetFeedActivity.class);

        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Give the notification a unique ID so that it can be updated later
        mNotificationManager.notify(notificationId++, mBuilder.build());
    }

    /**
     * Creates a new instance of a {@link LocationFinder} object,
     * implementing its onLocationChanged() method to guarantee
     * reception of current known location.
     * <p>
     * Once location is received, zooms the map fragment in to
     * the received location.
     */
    private void getCurrentLocation(final Handler.Callback cb) {
        final Context self = this;
        locationFinder = new LocationFinder(this) {
            @Override
            protected void onError(int type) {
                if (type == LocationFinder.LOCATION_SERVICES_OFF) {
                    startActivity(new Intent(self, SearchActivity.class));
                }
            }

            @Override
            public void onLocationChanged(Location location) {
                // Prevent further location updates from occurring
                locationFinder.stopLocationUpdates();

                // Disconnect from the googleApiClient
                locationFinder.disconnect();
                Message msg = new Message();
                Bundle args = new Bundle();
                args.putDouble("lat", location.getLatitude());
                args.putDouble("lon", location.getLongitude());
                msg.setData(args);
                new Handler(cb).sendMessage(msg);
            }
        };
    }

    /**
     * Starts the twitter stream service to receive
     * tweets at the specified latitude & longitude locations.
     *
     * @param latLng
     *          The {@link LatLng} location to open the service at.
     */
    private void startTwitterStreamService(LatLng latLng) {
        // Start and bind TwitterStreamService
        Intent startIntent = new Intent(this, TwitterStreamService.class);
        // put the radius and location on the intent
        startIntent.putExtra(getString(R.string.intent_lat), latLng.latitude);
        startIntent.putExtra(getString(R.string.intent_long), latLng.longitude);
        startService(startIntent);
    }

    @Override
    public void onListFragmentInteraction(TweetNode item) {
        Snackbar.make(findViewById(R.id.layout_tweet_feed), item.toString(), Snackbar.LENGTH_LONG).show();
    }

    @Override
    public boolean onClusterClick(Cluster cluster) {
        Log.d("CLUSTER_CLICK", Arrays.toString(cluster.getItems().toArray()));
        // TweetListFragment newFragment = new TweetListFragment(new ArrayList(cluster.getItems()));
        TweetListFragment newFragment = TweetListFragment.newInstance(1);
        Bundle args = new Bundle();
        ArrayList <TweetNode> clusterItems = new ArrayList<TweetNode>(cluster.getItems());

        args.putParcelableArrayList("TWEETS", clusterItems);
        newFragment.setArguments(args);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.activity_tweet_feed, newFragment);
        transaction.addToBackStack(null);
        transaction.commit();

        return true;
    }

    @Override
    public boolean onClusterItemClick(ClusterItem clusterItem) {
        Log.d("CLUSTER_ITEM_CLICK", clusterItem.getPosition().toString());
        TweetListFragment newFragment = TweetListFragment.newInstance(1);
        Bundle args = new Bundle();
        ArrayList <TweetNode> clusterItems = new ArrayList<TweetNode>();
        clusterItems.add((TweetNode) clusterItem);
        args.putParcelableArrayList("TWEETS", clusterItems);
        newFragment.setArguments(args);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.activity_tweet_feed, newFragment);
        transaction.addToBackStack(null);
        transaction.commit();

        return false;
    }

    @Override
    protected void onStop() {
        super.onStop();
        killService();
    }
    @Override
    protected void onDestroy () {
        super.onDestroy();
        killService();
    }

    private void killService () {
        Intent stopServiceIntent = new Intent(this, TwitterStreamService.class);
        stopService(stopServiceIntent);
    }

    public boolean onOptionItemIsSelected(MenuItem item) {
        // put code to handle actionbar items.. make sure you call super.onOptionItemSelected(item) as the default.

        switch (item.getItemId()) {
            default:
                return false;
        }

    }

    /**
     * Sets up the activity's {@link NavigationView}.
     *
     * Inflates its header and menu items.
     *
     * @param drawerLayout
     *          The drawer layout containing the {@link NavigationView}.
     *
     * @return
     *          An initialized {@link NavigationView} object.
     */
    private NavigationView setUpNavigationDrawer(final DrawerLayout drawerLayout) {
        NavigationView navDrawer = (NavigationView) findViewById(R.id.nav_drawer);

        // Find Nav Drawer header views
        View navHeader = navDrawer.getHeaderView(0);
        // Set up navDrawer menu onClick listeners
        setNavigationMenuItemClickListeners(navDrawer, drawerLayout);

        // Spawn async task to query twitter for user info and populate nav drawer header
        new GetTwitterUserInfoTask().execute(navHeader);

        return navDrawer;
    }

    /**
     * Sets the click listeners for each of the
     * {@link NavigationView}'s menu items.
     *
     * @param navDrawer
     *          The {@link NavigationView} containing
     *          the menu items.
     *
     * @param drawerLayout
     *          The {@link DrawerLayout} containing
     *          the {@link NavigationView}.
     */
    private void setNavigationMenuItemClickListeners(final NavigationView navDrawer,
                                                     final DrawerLayout drawerLayout) {
        Menu navMenu = navDrawer.getMenu();

        // Settings menu onClick listener
        navMenu.findItem(R.id.nav_settings).setOnMenuItemClickListener(
                new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        startActivity(new Intent(TweetFeedActivity.this, SettingsActivity.class));
                        drawerLayout.closeDrawer(navDrawer);

                        return true;
                    }
                }
        );

        // Search menu onClick listener
        navMenu.findItem(R.id.nav_search).setOnMenuItemClickListener(
                new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        startActivity(new Intent(TweetFeedActivity.this, SearchActivity.class));
                        drawerLayout.closeDrawer(navDrawer);

                        return true;
                    }
                }
        );

        // Logout menu onClick listener
        navMenu.findItem(R.id.nav_logout).setOnMenuItemClickListener(
                new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        // Clear relevant user auth data & reset Twitter accessToken
                        TwitterSession.getInstance().destroySession(TweetFeedActivity.this);
                        TwitterUtil.getInstance().setTwitterAccessToken(null);
                        TwitterUtil.getInstance().clearTwitterRequestToken();

                        startActivity(new Intent(TweetFeedActivity.this, LoginActivity.class));
                        drawerLayout.closeDrawer(navDrawer);

                        return true;
                    }
                }
        );
    }

    /**
     * Sets the {@link Toolbar}'s navigation button onClick listener.
     *
     * Opens the {@link NavigationView} drawer.
     *
     * @param toolbar
     *          The activity's toolbar containing the navigation button.
     *
     * @param drawerLayout
     *          The reference to the activity's main layout.
     *
     * @param navDrawer
     *          The {@link NavigationView} we are open on button click.
     */
    private void setNavigationButtonListener(Toolbar toolbar, final DrawerLayout drawerLayout,
                                             final View navDrawer) {
        // Set the toolbar's nav button on click listener
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // On nav button click, open the nav drawer
                drawerLayout.openDrawer(navDrawer);
            }
        });
    }

    /**
     * Asynchronous task to query twitter for the {@link User}'s
     * information to populate the nav drawer with.
     */
    private class GetTwitterUserInfoTask extends AsyncTask<View, Long, User> {
        private ViewGroup navHeader;
        private TextView twitterNameText;
        private TextView twitterHandleText;

        @Override
        protected User doInBackground(View... params) {
            User user = null;

            try {
                // Expect TextViews to be the first two params passed in
                navHeader = (ViewGroup) params[0];

                // Query twitter for twitter handle (screenName) and user data
                String screenName = twitter.getScreenName();
                user = twitter.showUser(screenName);
            } catch (TwitterException | IllegalStateException e) {
                // Log request error to twitter
                e.printStackTrace();
                Log.i(LOG_TAG, "Error occurred when attempting to contact Twitter.");
            }

            return user;
        }

        @Override
        protected void onPostExecute(User user) {
            twitterNameText = (TextView) navHeader.findViewById(R.id.twitter_name);
            twitterHandleText = (TextView) navHeader.findViewById(R.id.twitter_handle);
            // If no user was found then Toast user and return
            if (user == null) {
                Toast.makeText(TweetFeedActivity.this.getBaseContext(), "Unable to contact Twitter.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            new AsyncTask<String, Void, Bitmap> () {
                @Override
                protected Bitmap doInBackground(String... params) {
                    return ImageProcessor.bitmapFromUrl(params[0]);
                }

                @Override
                protected void onPostExecute(Bitmap mBitMap) {
                    super.onPostExecute(mBitMap);

                    navHeader.setBackground(new BitmapDrawable(getResources(), mBitMap));
                }
            }.execute(user.getProfileBannerURL());
            String twitterHandle = "@" + user.getScreenName();

            // This is executed in UI thread, so set nav drawer header values to user data
            twitterNameText.setText(user.getName());
            twitterHandleText.setText(twitterHandle);
        }
    }

    private class FetchTwitterFriends extends AsyncTask<Void, Void, Set<Long>> {

        @Override
        protected Set<Long> doInBackground(Void... params) {
            Set<Long> friendsSet = new HashSet<>();
            try {
                long userID = twitter.getId();
                IDs ids = twitter.getFriendsIDs(userID, -1);
                int remaining = ids.getIDs().length;
                while(remaining > 0) {
                    for (long id : ids.getIDs()) {
                        friendsSet.add(id);
                    }
                    ids = twitter.getFriendsIDs(userID, ids.getNextCursor());
                    remaining = ids.getIDs().length;
                }
            } catch (TwitterException e) {
                Log.i("TweetStreamService", "Error occurred when attempting to find user's friends." +
                        "It's probably because you're trying to fetch too many friends.");
            } catch (Exception e) {
                e.printStackTrace();
            }
            return friendsSet;
        }

        @Override
        protected void onPostExecute(Set<Long> friendsSet) {
            friends.addAll(friendsSet);
        }
    }

    private class FetchTwitterFollowers extends AsyncTask<Void, Void, Set<Long>> {

        @Override
        protected Set<Long> doInBackground(Void... params) {
            Set<Long> followersSet = new HashSet<>();
            try {
                long userID = twitter.getId();
                IDs ids = twitter.getFollowersIDs(userID, -1);
                int remaining = ids.getIDs().length;
                while(remaining > 0) {
                    for (long id : ids.getIDs()) {
                        followersSet.add(id);
                    }
                    ids = twitter.getFollowersIDs(userID, ids.getNextCursor());
                    remaining = ids.getIDs().length;
                }
            } catch (TwitterException e) {
                Log.i("TweetStreamService", "Error occurred when attempting to find user's followers. " +
                        "It's probably because you're trying to fetch too many followers.");
            } catch (Exception e) {
                e.printStackTrace();
            }
            return followersSet;
        }

        @Override
        protected void onPostExecute(Set<Long> followersSet) {
            followers.addAll(followersSet);
        }

    }

    public void setToolbar(){
        DrawerLayout drawerLayout = (DrawerLayout) findViewById(R.id.activity_tweet_feed);
        View navDrawer = setUpNavigationDrawer(drawerLayout);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.getMenu().clear();
        toolbar.setNavigationIcon(R.drawable.ic_nav_button);

        setNavigationButtonListener(toolbar, drawerLayout, navDrawer);
    }

}
