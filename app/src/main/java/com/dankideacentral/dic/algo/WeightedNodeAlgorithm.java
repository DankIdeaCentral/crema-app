package com.dankideacentral.dic.algo;

import com.dankideacentral.dic.model.WeightedNode;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterItem;
import com.google.maps.android.clustering.algo.Algorithm;
import com.google.maps.android.clustering.algo.StaticCluster;
import com.google.maps.android.geometry.Bounds;
import com.google.maps.android.geometry.Point;
import com.google.maps.android.projection.SphericalMercatorProjection;
import com.google.maps.android.quadtree.PointQuadTree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author srowhani
 * @param <T>
 */
public class WeightedNodeAlgorithm <T extends WeightedNode> implements Algorithm <T> {

    public static final int MAX_DISTANCE_AT_ZOOM = 80; // essentially 100 dp.

    private final Collection<WeightedItem<T>> mItems = new ArrayList<WeightedItem<T>>();

    private final PointQuadTree<WeightedItem<T>> mWeightedTree = new PointQuadTree<WeightedItem<T>>(0, 1, 0, 1);

    private static final SphericalMercatorProjection PROJECTION = new SphericalMercatorProjection(1);

    private static class WeightedItem <T extends WeightedNode> implements PointQuadTree.Item, Cluster<T> {
        private final T mClusterItem;
        private final Point mPoint;
        private final LatLng mPosition;
        private Set<T> singletonSet;

        private WeightedItem (T item) {
            mClusterItem = item;
            mPosition = item.getPosition();
            mPoint = PROJECTION.toPoint(mPosition);
            singletonSet = Collections.singleton(mClusterItem);
        }

        @Override
        public Point getPoint() {
            return mPoint;
        }

        @Override
        public LatLng getPosition() {
            return mPosition;
        }

        @Override
        public Set<T> getItems() {
            return singletonSet;
        }

        @Override
        public int getSize() {
            return mClusterItem.getSize();
        }

        @Override
        public int hashCode() {
            return mClusterItem.hashCode();
        };

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof WeightedItem<?>)) {
                return false;
            }

            return ((WeightedItem<?>) other).mClusterItem.equals(mClusterItem);
        }
    }


    @Override
    public void addItem(T item) {
        final WeightedItem<T> quadItem = new WeightedItem<T>(item);
        synchronized (mWeightedTree) {
            mItems.add(quadItem);
            mWeightedTree.add(quadItem);
        }
    }
    @Override
    public void addItems(Collection<T> items) {
        for (T item : items) {
            addItem(item);
        }
    }

    @Override
    public void clearItems() {
        synchronized (mWeightedTree) {
            mItems.clear();
            mWeightedTree.clear();
        }
    }

    @Override
    public void removeItem(T item) {
        final WeightedItem<T> weightedItem = new WeightedItem<>(item);
        synchronized (mWeightedTree) {
            mItems.remove(weightedItem);
            mWeightedTree.remove(weightedItem);
        }
    }

    @Override
    public Set<? extends Cluster<T>> getClusters(double zoom) {
        final int discreteZoom = (int) zoom;

        final double zoomSpecificSpan = MAX_DISTANCE_AT_ZOOM / Math.pow(2, discreteZoom) / 256;

        final Set<WeightedItem<T>> visitedCandidates = new HashSet<WeightedItem<T>>();
        final Set<Cluster<T>> results = new HashSet<Cluster<T>>();
        final Map<WeightedItem<T>, Double> distanceToCluster = new HashMap<WeightedItem<T>, Double>();
        final Map<WeightedItem<T>, StaticCluster<T>> itemToCluster = new HashMap<WeightedItem<T>, StaticCluster<T>>();

        synchronized (mWeightedTree) {
            for (WeightedItem<T> candidate : mItems) {
                if (visitedCandidates.contains(candidate)) {
                    continue;
                }

                Bounds searchBounds = createBoundsFromSpan(candidate.getPoint(), zoomSpecificSpan);
                Collection<WeightedItem<T>> clusterItems;
                clusterItems = mWeightedTree.search(searchBounds);
                if (clusterItems.size() == 1) {
                    // Only the current marker is in range. Just add the single item to the results.
                    results.add(candidate);
                    visitedCandidates.add(candidate);
                    distanceToCluster.put(candidate, 0d);
                    continue;
                }
                StaticCluster<T> cluster = new StaticCluster<T>(candidate.mClusterItem.getPosition());
                results.add(cluster);

                for (WeightedItem<T> clusterItem : clusterItems) {
                    Double existingDistance = distanceToCluster.get(clusterItem);
                    double distance = distanceSquared(clusterItem.getPoint(), candidate.getPoint());
                    if (existingDistance != null) {
                        // Item already belongs to another cluster. Check if it's closer to this cluster.
                        if (existingDistance < distance) {
                            continue;
                        }
                        // Move item to the closer cluster.
                        itemToCluster.get(clusterItem).remove(clusterItem.mClusterItem);
                    }
                    distanceToCluster.put(clusterItem, distance);
                    cluster.add(clusterItem.mClusterItem);
                    itemToCluster.put(clusterItem, cluster);
                }
                visitedCandidates.addAll(clusterItems);
            }
        }
        return results;
    }

    private Bounds createBoundsFromSpan(Point p, double span) {
        // TODO: Use a span that takes into account the visual size of the marker, not just its
        // LatLng.
        double halfSpan = span / 2;
        return new Bounds(
                p.x - halfSpan, p.x + halfSpan,
                p.y - halfSpan, p.y + halfSpan);
    }
    private double distanceSquared(Point a, Point b) {
        return (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y);
    }
    @Override
    public Collection<T> getItems() {
        return null;
    }
}
