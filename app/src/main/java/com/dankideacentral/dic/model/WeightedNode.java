package com.dankideacentral.dic.model;

import android.os.Parcelable;

import com.google.maps.android.clustering.ClusterItem;

/**
 * Created by srowhani on 10/21/16.
 */

public abstract class WeightedNode implements ClusterItem {
    public abstract int getWeight();

}

