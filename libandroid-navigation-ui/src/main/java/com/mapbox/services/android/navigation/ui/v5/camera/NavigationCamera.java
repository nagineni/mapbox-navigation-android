package com.mapbox.services.android.navigation.ui.v5.camera;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.location.Location;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;

import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdate;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin;
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.CameraMode;
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigation;
import com.mapbox.services.android.navigation.v5.navigation.camera.Camera;
import com.mapbox.services.android.navigation.v5.navigation.camera.RouteInformation;
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress;
import com.mapbox.services.android.navigation.v5.utils.MathUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

import static com.mapbox.services.android.navigation.v5.navigation.NavigationConstants.NAVIGATION_MAX_CAMERA_ADJUSTMENT_ANIMATION_DURATION;
import static com.mapbox.services.android.navigation.v5.navigation.NavigationConstants.NAVIGATION_MIN_CAMERA_TILT_ADJUSTMENT_ANIMATION_DURATION;
import static com.mapbox.services.android.navigation.v5.navigation.NavigationConstants.NAVIGATION_MIN_CAMERA_ZOOM_ADJUSTMENT_ANIMATION_DURATION;

/**
 * Updates the map camera while navigating.
 * <p>
 * This class listens to the progress of {@link MapboxNavigation} and moves
 * the {@link MapboxMap} camera based on the location updates.
 *
 * @since 0.6.0
 */
public class NavigationCamera implements LifecycleObserver {

  private static final int ONE_POINT = 1;

  private MapboxMap mapboxMap;
  private LocationLayerPlugin locationLayer;
  private MapboxNavigation navigation;
  private RouteInformation currentRouteInformation;
  private RouteProgress currentRouteProgress;
  private boolean trackingEnabled;
  private ProgressChangeListener progressChangeListener = new ProgressChangeListener() {
    @Override
    public void onProgressChange(Location location, RouteProgress routeProgress) {
      currentRouteProgress = routeProgress;
      if (trackingEnabled) {
        currentRouteInformation = buildRouteInformationFromLocation(location, routeProgress);
        adjustCameraFromLocation(currentRouteInformation);
      }
    }
  };

  @Retention(RetentionPolicy.SOURCE)
  @IntDef( {NAVIGATION_TRACKING_MODE_GPS, NAVIGATION_TRACKING_MODE_NORTH})
  public @interface TrackingMode {
  }

  /**
   * Camera tracks the user location, with bearing provided by the location update.
   * <p>
   * Equivalent of the {@link CameraMode#TRACKING_GPS}.
   */
  public static final int NAVIGATION_TRACKING_MODE_GPS = 0;

  /**
   * Camera tracks the user location, with bearing always set to north (0).
   * <p>
   * Equivalent of the {@link CameraMode#TRACKING_GPS_NORTH}.
   */
  public static final int NAVIGATION_TRACKING_MODE_NORTH = 1;

  @TrackingMode
  private int trackingCameraMode = NAVIGATION_TRACKING_MODE_GPS;

  /**
   * Creates an instance of {@link NavigationCamera}.
   *
   * @param mapboxMap     for moving the camera
   * @param navigation    for listening to location updates
   * @param locationLayer for managing camera mode
   * @since 0.6.0
   */
  public NavigationCamera(@NonNull MapboxMap mapboxMap, @NonNull MapboxNavigation navigation,
                          @NonNull LocationLayerPlugin locationLayer) {
    this.mapboxMap = mapboxMap;
    this.navigation = navigation;
    this.locationLayer = locationLayer;
    initialize();
  }

  /**
   * Creates an instance of {@link NavigationCamera}.
   * <p>
   * Camera will start tracking current user location by default.
   *
   * @param mapboxMap     for moving the camera
   * @param locationLayer for managing camera mode
   * @since 0.15.0
   */
  public NavigationCamera(@NonNull MapboxMap mapboxMap, LocationLayerPlugin locationLayer) {
    this.mapboxMap = mapboxMap;
    this.locationLayer = locationLayer;
    setTrackingEnabled(true);
  }

  /**
   * Used for testing only.
   */
  NavigationCamera(MapboxMap mapboxMap, MapboxNavigation navigation, ProgressChangeListener progressChangeListener,
                   LocationLayerPlugin locationLayer) {
    this.mapboxMap = mapboxMap;
    this.locationLayer = locationLayer;
    this.navigation = navigation;
    this.progressChangeListener = progressChangeListener;
  }

  /**
   * Called when beginning navigation with a route.
   *
   * @param route used to update route information
   * @since 0.6.0
   */
  public void start(DirectionsRoute route) {
    if (route != null) {
      currentRouteInformation = buildRouteInformationFromRoute(route);
    }
    navigation.addProgressChangeListener(progressChangeListener);
  }

  /**
   * Called during rotation to update route information.
   *
   * @param location used to update route information
   * @since 0.6.0
   */
  public void resume(Location location) {
    if (location != null) {
      currentRouteInformation = buildRouteInformationFromLocation(location, null);
    }
    navigation.addProgressChangeListener(progressChangeListener);
  }

  /**
   * Setter for whether or not the camera should follow the location.
   *
   * @param trackingEnabled true if should track, false if should not
   * @since 0.6.0
   */
  public void updateCameraTrackingLocation(boolean trackingEnabled) {
    setTrackingEnabled(trackingEnabled);
  }

  /**
   * Updates the {@link TrackingMode} that's going to be used when camera tracking is enabled.
   *
   * @param trackingMode the tracking mode
   * @since 0.21.0
   */
  public void updateCameraTrackingMode(@TrackingMode int trackingMode) {
    trackingCameraMode = trackingMode;
    setCameraMode();
  }

  /**
   * Getter for current state of tracking.
   *
   * @return true if tracking, false if not
   * @since 0.6.0
   */
  public boolean isTrackingEnabled() {
    return trackingEnabled;
  }

  /**
   * Getter for {@link TrackingMode} that's being used when tracking is enabled.
   *
   * @return tracking mode
   * @since 0.21.0
   */
  @TrackingMode
  public int getCameraTrackingMode() {
    return trackingCameraMode;
  }

  /**
   * Enables tracking and updates zoom/tilt based on the available route information.
   *
   * @since 0.6.0
   */
  public void resetCameraPosition() {
    setTrackingEnabled(true);
    if (currentRouteInformation != null) {
      Camera camera = navigation.getCameraEngine();
      if (camera instanceof DynamicCamera) {
        ((DynamicCamera) camera).forceResetZoomLevel();
      }
      adjustCameraFromLocation(currentRouteInformation);
    }
  }

  public void showRouteOverview(int[] padding) {
    setTrackingEnabled(false);
    RouteInformation routeInformation = buildRouteInformationFromProgress(currentRouteProgress);
    animateCameraForRouteOverview(routeInformation, padding);
  }

  /**
   * Call in {@link FragmentActivity#onStart()} to properly add the {@link ProgressChangeListener}
   * for the camera and prevent any leaks or further updates.
   *
   * @since 0.15.0
   */
  @OnLifecycleEvent(Lifecycle.Event.ON_START)
  public void onStart() {
    if (navigation != null) {
      navigation.addProgressChangeListener(progressChangeListener);
    }
  }

  /**
   * Call in {@link FragmentActivity#onStop()} to properly remove the {@link ProgressChangeListener}
   * for the camera and prevent any leaks or further updates.
   *
   * @since 0.15.0
   */
  @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
  public void onStop() {
    if (navigation != null) {
      navigation.removeProgressChangeListener(progressChangeListener);
    }
  }

  public void addProgressChangeListener(MapboxNavigation navigation) {
    this.navigation = navigation;
    navigation.setCameraEngine(new DynamicCamera(mapboxMap));
    navigation.addProgressChangeListener(progressChangeListener);
  }

  private void initialize() {
    navigation.setCameraEngine(new DynamicCamera(mapboxMap));
    setTrackingEnabled(true);
  }

  /**
   * Creates a camera position based on the given route.
   * <p>
   * From the {@link DirectionsRoute}, an initial bearing and target position are created.
   * Then using a preset tilt and zoom (based on screen orientation), a {@link CameraPosition} is built.
   *
   * @param route used to build the camera position
   * @return camera position to be animated to
   */
  @NonNull
  private RouteInformation buildRouteInformationFromRoute(DirectionsRoute route) {
    return RouteInformation.create(route, null, null);
  }

  /**
   * Creates a camera position based on the given location.
   * <p>
   * From the {@link Location}, a target position is created.
   * Then using a preset tilt and zoom (based on screen orientation), a {@link CameraPosition} is built.
   *
   * @param location used to build the camera position
   * @return camera position to be animated to
   */
  @NonNull
  private RouteInformation buildRouteInformationFromLocation(Location location, RouteProgress routeProgress) {
    return RouteInformation.create(null, location, routeProgress);
  }

  @NonNull
  private RouteInformation buildRouteInformationFromProgress(RouteProgress routeProgress) {
    if (routeProgress == null) {
      return RouteInformation.create(null, null, null);
    }
    return RouteInformation.create(routeProgress.directionsRoute(), null, null);
  }

  private void setTrackingEnabled(boolean trackingEnabled) {
    this.trackingEnabled = trackingEnabled;
    setCameraMode();
  }

  private void animateCameraForRouteOverview(RouteInformation routeInformation, int[] padding) {
    Camera cameraEngine = navigation.getCameraEngine();
    List<Point> routePoints = cameraEngine.overview(routeInformation);
    if (!routePoints.isEmpty()) {
      animateMapboxMapForRouteOverview(padding, routePoints);
    }
  }

  private void animateMapboxMapForRouteOverview(int[] padding, List<Point> routePoints) {
    if (routePoints.size() <= ONE_POINT) {
      return;
    }
    CameraUpdate resetUpdate = buildResetCameraUpdate();
    final CameraUpdate overviewUpdate = buildOverviewCameraUpdate(padding, routePoints);
    mapboxMap.animateCamera(resetUpdate, 150,
      new CameraOverviewCancelableCallback(overviewUpdate, mapboxMap)
    );
  }

  @NonNull
  private CameraUpdate buildResetCameraUpdate() {
    CameraPosition resetPosition = new CameraPosition.Builder().tilt(0).bearing(0).build();
    return CameraUpdateFactory.newCameraPosition(resetPosition);
  }

  @NonNull
  private CameraUpdate buildOverviewCameraUpdate(int[] padding, List<Point> routePoints) {
    final LatLngBounds routeBounds = convertRoutePointsToLatLngBounds(routePoints);
    return CameraUpdateFactory.newLatLngBounds(
      routeBounds, padding[0], padding[1], padding[2], padding[3]
    );
  }

  private LatLngBounds convertRoutePointsToLatLngBounds(List<Point> routePoints) {
    List<LatLng> latLngs = new ArrayList<>();
    for (Point routePoint : routePoints) {
      latLngs.add(new LatLng(routePoint.latitude(), routePoint.longitude()));
    }
    return new LatLngBounds.Builder()
      .includes(latLngs)
      .build();
  }

  private void setCameraMode() {
    @CameraMode.Mode int mode;
    if (trackingEnabled) {
      if (trackingCameraMode == NAVIGATION_TRACKING_MODE_GPS) {
        mode = CameraMode.TRACKING_GPS;
      } else if (trackingCameraMode == NAVIGATION_TRACKING_MODE_NORTH) {
        mode = CameraMode.TRACKING_GPS_NORTH;
      } else {
        mode = CameraMode.NONE;
        Timber.e("Using unsupported camera tracking mode - %d.", trackingCameraMode);
      }
    } else {
      mode = CameraMode.NONE;
    }

    if (mode != locationLayer.getCameraMode()) {
      locationLayer.setCameraMode(mode);
    }
  }

  /**
   * Updates the camera's zoom and tilt while tracking.
   *
   * @param routeInformation with location data
   */
  private void adjustCameraFromLocation(RouteInformation routeInformation) {
    Camera cameraEngine = navigation.getCameraEngine();

    float tilt = (float) cameraEngine.tilt(routeInformation);
    double zoom = cameraEngine.zoom(routeInformation);

    locationLayer.zoomWhileTracking(zoom, getZoomAnimationDuration(zoom));
    locationLayer.tiltWhileTracking(tilt, getTiltAnimationDuration(tilt));
  }

  private long getZoomAnimationDuration(double zoom) {
    double zoomDiff = Math.abs(mapboxMap.getCameraPosition().zoom - zoom);
    return (long) MathUtils.clamp(
      500 * zoomDiff,
      NAVIGATION_MIN_CAMERA_ZOOM_ADJUSTMENT_ANIMATION_DURATION,
      NAVIGATION_MAX_CAMERA_ADJUSTMENT_ANIMATION_DURATION);
  }

  private long getTiltAnimationDuration(double tilt) {
    double tiltDiff = Math.abs(mapboxMap.getCameraPosition().tilt - tilt);
    return (long) MathUtils.clamp(
      500 * tiltDiff,
      NAVIGATION_MIN_CAMERA_TILT_ADJUSTMENT_ANIMATION_DURATION,
      NAVIGATION_MAX_CAMERA_ADJUSTMENT_ANIMATION_DURATION);
  }
}
