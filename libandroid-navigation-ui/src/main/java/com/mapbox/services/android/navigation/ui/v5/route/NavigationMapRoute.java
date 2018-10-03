package com.mapbox.services.android.navigation.ui.v5.route;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;
import android.support.annotation.StyleRes;
import android.support.v4.content.ContextCompat;
import android.support.v7.content.res.AppCompatResources;

import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.api.directions.v5.models.RouteLeg;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.style.expressions.Expression;
import com.mapbox.mapboxsdk.style.layers.Layer;
import com.mapbox.mapboxsdk.style.layers.LineLayer;
import com.mapbox.mapboxsdk.style.layers.Property;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.services.android.navigation.ui.v5.R;
import com.mapbox.services.android.navigation.ui.v5.utils.MapImageUtils;
import com.mapbox.services.android.navigation.ui.v5.utils.MapUtils;
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigation;
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import static com.mapbox.mapboxsdk.style.expressions.Expression.color;
import static com.mapbox.mapboxsdk.style.expressions.Expression.exponential;
import static com.mapbox.mapboxsdk.style.expressions.Expression.get;
import static com.mapbox.mapboxsdk.style.expressions.Expression.interpolate;
import static com.mapbox.mapboxsdk.style.expressions.Expression.literal;
import static com.mapbox.mapboxsdk.style.expressions.Expression.match;
import static com.mapbox.mapboxsdk.style.expressions.Expression.stop;
import static com.mapbox.mapboxsdk.style.expressions.Expression.zoom;
import static com.mapbox.mapboxsdk.style.layers.Property.NONE;
import static com.mapbox.mapboxsdk.style.layers.Property.VISIBLE;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.visibility;

/**
 * Provide a route using {@link NavigationMapRoute#addRoutes(List)} and a route will be drawn using
 * runtime styling. The route will automatically be placed below all labels independent of specific
 * style. If the map styles changed when a routes drawn on the map, the route will automatically be
 * redrawn onto the new map style. If during a navigation session, the user gets re-routed, the
 * route line will be redrawn to reflect the new geometry. To remove the route from the map, use
 * {@link NavigationMapRoute#removeRoute()}.
 * <p>
 * You are given the option when first constructing an instance of this class to pass in a style
 * resource. This allows for custom colorizing and line scaling of the route. Inside your
 * applications {@code style.xml} file, you extend {@code <style name="NavigationMapRoute">} and
 * change some or all the options currently offered. If no style files provided in the constructor,
 * the default style will be used.
 *
 * @since 0.4.0
 */
public class NavigationMapRoute implements MapView.OnMapChangedListener, LifecycleObserver {

  @StyleRes
  private int styleRes;
  @ColorInt
  private int routeDefaultColor;
  @ColorInt
  private int routeModerateColor;
  @ColorInt
  private int routeSevereColor;
  @ColorInt
  private int alternativeRouteDefaultColor;
  @ColorInt
  private int alternativeRouteModerateColor;
  @ColorInt
  private int alternativeRouteSevereColor;
  @ColorInt
  private int alternativeRouteShieldColor;
  @ColorInt
  private int routeShieldColor;
  @ColorInt
  private int arrowColor;
  @ColorInt
  private int arrowBorderColor;
  @DrawableRes
  private int originWaypointIcon;
  @DrawableRes
  private int destinationWaypointIcon;

  private final HashMap<LineString, DirectionsRoute> routeLineStrings;
  private final List<FeatureCollection> featureCollections;
  private final List<DirectionsRoute> directionsRoutes;
  private final List<String> layerIds;
  private final MapboxMap mapboxMap;
  private final MapView mapView;
  private final ProgressChangeListener progressChangeListener = new MapRouteProgressChangeListener(this);
  private final MapRouteClickListener mapRouteClickListener = new MapRouteClickListener(this);

  private int primaryRouteIndex;
  private float routeScale;
  private float alternativeRouteScale;
  private String belowLayer;
  private boolean alternativesVisible = true;
  private MapboxNavigation navigation;
  private MapRouteArrow routeArrow;

  /**
   * Construct an instance of {@link NavigationMapRoute}.
   *
   * @param mapView   the MapView to apply the route to
   * @param mapboxMap the MapboxMap to apply route with
   * @since 0.4.0
   */
  public NavigationMapRoute(@NonNull MapView mapView, @NonNull MapboxMap mapboxMap) {
    this(null, mapView, mapboxMap, R.style.NavigationMapRoute);
  }

  /**
   * Construct an instance of {@link NavigationMapRoute}.
   *
   * @param mapView    the MapView to apply the route to
   * @param mapboxMap  the MapboxMap to apply route with
   * @param belowLayer optionally pass in a layer id to place the route line below
   * @since 0.4.0
   */
  public NavigationMapRoute(@NonNull MapView mapView, @NonNull MapboxMap mapboxMap,
                            @Nullable String belowLayer) {
    this(null, mapView, mapboxMap, R.style.NavigationMapRoute, belowLayer);
  }

  /**
   * Construct an instance of {@link NavigationMapRoute}.
   *
   * @param navigation an instance of the {@link MapboxNavigation} object. Passing in null means
   *                   your route won't consider rerouting during a navigation session.
   * @param mapView    the MapView to apply the route to
   * @param mapboxMap  the MapboxMap to apply route with
   * @since 0.4.0
   */
  public NavigationMapRoute(@Nullable MapboxNavigation navigation, @NonNull MapView mapView,
                            @NonNull MapboxMap mapboxMap) {
    this(navigation, mapView, mapboxMap, R.style.NavigationMapRoute);
  }

  /**
   * Construct an instance of {@link NavigationMapRoute}.
   *
   * @param navigation an instance of the {@link MapboxNavigation} object. Passing in null means
   *                   your route won't consider rerouting during a navigation session.
   * @param mapView    the MapView to apply the route to
   * @param mapboxMap  the MapboxMap to apply route with
   * @param belowLayer optionally pass in a layer id to place the route line below
   * @since 0.4.0
   */
  public NavigationMapRoute(@Nullable MapboxNavigation navigation, @NonNull MapView mapView,
                            @NonNull MapboxMap mapboxMap, @Nullable String belowLayer) {
    this(navigation, mapView, mapboxMap, R.style.NavigationMapRoute, belowLayer);
  }

  /**
   * Construct an instance of {@link NavigationMapRoute}.
   *
   * @param navigation an instance of the {@link MapboxNavigation} object. Passing in null means
   *                   your route won't consider rerouting during a navigation session.
   * @param mapView    the MapView to apply the route to
   * @param mapboxMap  the MapboxMap to apply route with
   * @param styleRes   a style resource with custom route colors, scale, etc.
   */
  public NavigationMapRoute(@Nullable MapboxNavigation navigation, @NonNull MapView mapView,
                            @NonNull MapboxMap mapboxMap, @StyleRes int styleRes) {
    this(navigation, mapView, mapboxMap, styleRes, null);
  }

  /**
   * Construct an instance of {@link NavigationMapRoute}.
   *
   * @param navigation an instance of the {@link MapboxNavigation} object. Passing in null means
   *                   your route won't consider rerouting during a navigation session.
   * @param mapView    the MapView to apply the route to
   * @param mapboxMap  the MapboxMap to apply route with
   * @param styleRes   a style resource with custom route colors, scale, etc.
   * @param belowLayer optionally pass in a layer id to place the route line below
   */
  public NavigationMapRoute(@Nullable MapboxNavigation navigation, @NonNull MapView mapView,
                            @NonNull MapboxMap mapboxMap, @StyleRes int styleRes,
                            @Nullable String belowLayer) {
    this.styleRes = styleRes;
    this.mapView = mapView;
    this.mapboxMap = mapboxMap;
    this.navigation = navigation;
    this.belowLayer = belowLayer;
    featureCollections = new ArrayList<>();
    directionsRoutes = new ArrayList<>();
    routeLineStrings = new HashMap<>();
    layerIds = new ArrayList<>();
    initialize();
    addListeners();
  }

  /**
   * Allows adding a single primary route for the user to traverse along. No alternative routes will
   * be drawn on top of the map.
   *
   * @param directionsRoute the directions route which you'd like to display on the map
   * @since 0.4.0
   */
  public void addRoute(DirectionsRoute directionsRoute) {
    List<DirectionsRoute> routes = new ArrayList<>();
    routes.add(directionsRoute);
    addRoutes(routes);
  }

  /**
   * Provide a list of {@link DirectionsRoute}s, the primary route will default to the first route
   * in the directions route list. All other routes in the list will be drawn on the map using the
   * alternative route style.
   *
   * @param directionsRoutes a list of direction routes, first one being the primary and the rest of
   *                         the routes are considered alternatives.
   * @since 0.8.0
   */
  public void addRoutes(@NonNull @Size(min = 1) List<DirectionsRoute> directionsRoutes) {
    removeAllRoutes();
    this.directionsRoutes.addAll(directionsRoutes);
    primaryRouteIndex = 0;
    alternativesVisible = directionsRoutes.size() > 1;
    generateFeatureCollectionList(directionsRoutes);
  }

  // TODO javadoc
  public void removeRoute() {
    removeAllRoutes();
  }

  /**
   * Add a {@link OnRouteSelectionChangeListener} to know which route the user has currently
   * selected as their primary route.
   *
   * @param onRouteSelectionChangeListener a listener which lets you know when the user has changed
   *                                       the primary route and provides the current direction
   *                                       route which the user has selected
   * @since 0.8.0
   */
  public void setOnRouteSelectionChangeListener(
    @Nullable OnRouteSelectionChangeListener onRouteSelectionChangeListener) {
    mapRouteClickListener.setOnRouteSelectionChangeListener(onRouteSelectionChangeListener);
  }

  /**
   * Toggle whether or not you'd like the map to display the alternative routes. This options great
   * for when the user actually begins the navigation session and alternative routes aren't needed
   * anymore.
   *
   * @param alternativesVisible true if you'd like alternative routes to be displayed on the map,
   *                            else false
   * @since 0.8.0
   */
  public void showAlternativeRoutes(boolean alternativesVisible) {
    this.alternativesVisible = alternativesVisible;
    mapRouteClickListener.updateAlternativesVisible(alternativesVisible);
    toggleAlternativeVisibility(alternativesVisible);
  }

  // TODO private listener?
  @Override
  public void onMapChanged(int change) {
    if (change == MapView.DID_FINISH_LOADING_STYLE) {
      placeRouteBelow();
      routeArrow = new MapRouteArrow(mapView, mapboxMap, arrowColor, arrowBorderColor);
      drawRoutes();
      addDirectionWaypoints();
      showAlternativeRoutes(alternativesVisible);
    }
  }

  // TODO javadoc
  public void addProgressChangeListener(MapboxNavigation navigation) {
    this.navigation = navigation;
    navigation.addProgressChangeListener(progressChangeListener);
  }

  // TODO javadoc
  public void removeProgressChangeListener(MapboxNavigation navigation) {
    if (navigation != null) {
      navigation.removeProgressChangeListener(progressChangeListener);
    }
  }

  /**
   * This method should be called only if you have passed {@link MapboxNavigation}
   * into the constructor.
   * <p>
   * This method will add the {@link ProgressChangeListener} that was originally added so updates
   * to the {@link MapboxMap} continue.
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
   * This method should be called only if you have passed {@link MapboxNavigation}
   * into the constructor.
   * <p>
   * This method will remove the {@link ProgressChangeListener} that was originally added so updates
   * to the {@link MapboxMap} discontinue.
   *
   * @since 0.15.0
   */
  @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
  public void onStop() {
    if (navigation != null) {
      navigation.removeProgressChangeListener(progressChangeListener);
    }
  }

  void addUpcomingManeuverArrow(RouteProgress routeProgress) {
    routeArrow.addUpcomingManeuverArrow(routeProgress);
  }

  List<DirectionsRoute> retrieveDirectionsRoutes() {
    return directionsRoutes;
  }

  void updatePrimaryRouteIndex(int primaryRouteIndex) {
    this.primaryRouteIndex = primaryRouteIndex;
  }

  int retrievePrimaryRouteIndex() {
    return primaryRouteIndex;
  }

  HashMap<LineString, DirectionsRoute> retrieveRouteLineStrings() {
    return routeLineStrings;
  }

  void updateRoutes() {
    // Update all route geometries to reflect their appropriate colors depending on if they are
    // alternative or primary.
    for (FeatureCollection featureCollection : featureCollections) {
      if (!(featureCollection.features().get(0).geometry() instanceof Point)) {
        int index = featureCollection.features().get(0).getNumberProperty(RouteConstants.INDEX_KEY).intValue();
        updatePrimaryShieldRoute(String.format(Locale.US, RouteConstants.ID_FORMAT,
          RouteConstants.GENERIC_ROUTE_SHIELD_LAYER_ID, index), index);
        updatePrimaryRoute(String.format(Locale.US, RouteConstants.ID_FORMAT,
          RouteConstants.GENERIC_ROUTE_LAYER_ID, index), index);
      }
    }
  }

  /**
   * Loops through all the route layers stored inside the layerId list and toggles the visibility.
   * if the layerId matches the primary route index, we skip since we still want that route to be
   * displayed.
   */
  private void toggleAlternativeVisibility(boolean visible) {
    for (String layerId : layerIds) {
      if (layerId.contains(String.valueOf(primaryRouteIndex))
        || layerId.contains(RouteConstants.WAYPOINT_LAYER_ID)) {
        continue;
      }
      Layer layer = mapboxMap.getLayer(layerId);
      if (layer != null) {
        layer.setProperties(
          visibility(visible ? VISIBLE : NONE)
        );
      }
    }
  }

  private void drawRoutes() {
    // Add all the sources, the list is traversed backwards to ensure the primary route always gets
    // drawn on top of the others since it initially has a index of zero.
    for (int i = featureCollections.size() - 1; i >= 0; i--) {
      MapUtils.updateMapSourceFromFeatureCollection(
        mapboxMap, featureCollections.get(i),
        featureCollections.get(i).features().get(0).getStringProperty(RouteConstants.SOURCE_KEY)
      );

      // Get some required information for the next step
      String sourceId = featureCollections.get(i).features()
        .get(0).getStringProperty(RouteConstants.SOURCE_KEY);
      int index = featureCollections.indexOf(featureCollections.get(i));

      // Add the layer IDs to a list so we can quickly remove them when needed without traversing
      // through all the map layers.
      layerIds.add(String.format(Locale.US, RouteConstants.ID_FORMAT,
        RouteConstants.GENERIC_ROUTE_SHIELD_LAYER_ID, index));
      layerIds.add(String.format(Locale.US, RouteConstants.ID_FORMAT,
        RouteConstants.GENERIC_ROUTE_LAYER_ID, index));

      // Add the route shield first followed by the route to ensure the shield is always on the
      // bottom.
      addRouteShieldLayer(layerIds.get(layerIds.size() - 2), sourceId, index);
      addRouteLayer(layerIds.get(layerIds.size() - 1), sourceId, index);
    }
  }

  private void removeAllRoutes() {
    removeLayerIds();
    routeArrow.updateVisibilityTo(false);
    clearRouteListData();
  }

  private void generateFeatureCollectionList(List<DirectionsRoute> routes) {
    new FeatureProcessingTask(routes, featureCollections, routeLineStrings, new OnFeaturesProcessedCallback() {
      @Override
      public void onFeaturesProcessed() {
        DirectionsRoute primaryRoute = directionsRoutes.get(primaryRouteIndex);
        FeatureCollection waypointFeatureCollection = buildWaypointFeatureCollectionFrom(primaryRoute);
        featureCollections.add(waypointFeatureCollection);
        drawRoutes();
        addDirectionWaypoints();
      }
    }).execute();
  }

  /**
   * The routes also display an icon for each waypoint in the route, we use symbol layers for this.
   */
  private FeatureCollection buildWaypointFeatureCollectionFrom(DirectionsRoute route) {
    final List<Feature> waypointFeatures = new ArrayList<>();
    for (RouteLeg leg : route.legs()) {
      waypointFeatures.add(getPointFromLineString(leg, 0));
      waypointFeatures.add(getPointFromLineString(leg, leg.steps().size() - 1));
    }
    return FeatureCollection.fromFeatures(waypointFeatures);
  }

  private void addDirectionWaypoints() {
    MapUtils.updateMapSourceFromFeatureCollection(
      mapboxMap, featureCollections.get(featureCollections.size() - 1), RouteConstants.WAYPOINT_SOURCE_ID);
    drawWaypointMarkers(mapboxMap,
      AppCompatResources.getDrawable(mapView.getContext(), originWaypointIcon),
      AppCompatResources.getDrawable(mapView.getContext(), destinationWaypointIcon)
    );
  }

  private void updatePrimaryRoute(String layerId, int index) {
    Layer layer = mapboxMap.getLayer(layerId);
    if (layer != null) {
      layer.setProperties(
        PropertyFactory.lineColor(match(
          Expression.toString(get(RouteConstants.CONGESTION_KEY)),
          color(index == primaryRouteIndex ? routeDefaultColor : alternativeRouteDefaultColor),
          stop("moderate", color(index == primaryRouteIndex ? routeModerateColor : alternativeRouteModerateColor)),
          stop("heavy", color(index == primaryRouteIndex ? routeSevereColor : alternativeRouteSevereColor)),
          stop("severe", color(index == primaryRouteIndex ? routeSevereColor : alternativeRouteSevereColor))
          )
        )
      );
      if (index == primaryRouteIndex) {
        mapboxMap.removeLayer(layer);
        mapboxMap.addLayerBelow(layer, RouteConstants.WAYPOINT_LAYER_ID);
      }
    }
  }

  private void updatePrimaryShieldRoute(String layerId, int index) {
    Layer layer = mapboxMap.getLayer(layerId);
    if (layer != null) {
      layer.setProperties(
        PropertyFactory.lineColor(index == primaryRouteIndex ? routeShieldColor : alternativeRouteShieldColor)
      );
      if (index == primaryRouteIndex) {
        mapboxMap.removeLayer(layer);
        mapboxMap.addLayerBelow(layer, RouteConstants.WAYPOINT_LAYER_ID);
      }
    }
  }

  /**
   * Add the route layer to the map either using the custom style values or the default.
   */
  private void addRouteLayer(String layerId, String sourceId, int index) {
    float scale = index == primaryRouteIndex ? routeScale : alternativeRouteScale;
    Layer routeLayer = new LineLayer(layerId, sourceId).withProperties(
      PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
      PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
      PropertyFactory.lineWidth(interpolate(
        exponential(1.5f), zoom(),
        stop(4f, 3f * scale),
        stop(10f, 4f * scale),
        stop(13f, 6f * scale),
        stop(16f, 10f * scale),
        stop(19f, 14f * scale),
        stop(22f, 18f * scale)
        )
      ),
      PropertyFactory.lineColor(match(
        Expression.toString(get(RouteConstants.CONGESTION_KEY)),
        color(index == primaryRouteIndex ? routeDefaultColor : alternativeRouteDefaultColor),
        stop("moderate", color(index == primaryRouteIndex ? routeModerateColor : alternativeRouteModerateColor)),
        stop("heavy", color(index == primaryRouteIndex ? routeSevereColor : alternativeRouteSevereColor)),
        stop("severe", color(index == primaryRouteIndex ? routeSevereColor : alternativeRouteSevereColor))
        )
      )
    );
    MapUtils.addLayerToMap(mapboxMap, routeLayer, belowLayer);
  }

  private void removeLayerIds() {
    if (!layerIds.isEmpty()) {
      for (String id : layerIds) {
        mapboxMap.removeLayer(id);
      }
    }
  }

  private void clearRouteListData() {
    if (!directionsRoutes.isEmpty()) {
      directionsRoutes.clear();
    }
    if (!routeLineStrings.isEmpty()) {
      routeLineStrings.clear();
    }
    if (!featureCollections.isEmpty()) {
      featureCollections.clear();
    }
  }

  /**
   * Add the route shield layer to the map either using the custom style values or the default.
   */
  private void addRouteShieldLayer(String layerId, String sourceId, int index) {
    float scale = index == primaryRouteIndex ? routeScale : alternativeRouteScale;
    Layer routeLayer = new LineLayer(layerId, sourceId).withProperties(
      PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
      PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
      PropertyFactory.lineWidth(interpolate(
        exponential(1.5f), zoom(),
        stop(10f, 7f),
        stop(14f, 10.5f * scale),
        stop(16.5f, 15.5f * scale),
        stop(19f, 24f * scale),
        stop(22f, 29f * scale)
        )
      ),
      PropertyFactory.lineColor(
        index == primaryRouteIndex ? routeShieldColor : alternativeRouteShieldColor)
    );
    MapUtils.addLayerToMap(mapboxMap, routeLayer, belowLayer);
  }

  /**
   * Loads in all the custom values the user might have set such as colors and line width scalars.
   * Anything they didn't set, results in using the default values.
   */
  private void getAttributes() {
    Context context = mapView.getContext();
    TypedArray typedArray = context.obtainStyledAttributes(styleRes, R.styleable.NavigationMapRoute);

    // Primary Route attributes
    routeDefaultColor = typedArray.getColor(R.styleable.NavigationMapRoute_routeColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_layer_blue));
    routeModerateColor = typedArray.getColor(
      R.styleable.NavigationMapRoute_routeModerateCongestionColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_layer_congestion_yellow));
    routeSevereColor = typedArray.getColor(
      R.styleable.NavigationMapRoute_routeSevereCongestionColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_layer_congestion_red));
    routeShieldColor = typedArray.getColor(R.styleable.NavigationMapRoute_routeShieldColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_shield_layer_color));
    routeScale = typedArray.getFloat(R.styleable.NavigationMapRoute_routeScale, 1.0f);

    // Secondary Routes attributes
    alternativeRouteDefaultColor = typedArray.getColor(
      R.styleable.NavigationMapRoute_alternativeRouteColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_alternative_color));
    alternativeRouteModerateColor = typedArray.getColor(
      R.styleable.NavigationMapRoute_alternativeRouteModerateCongestionColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_alternative_congestion_yellow));
    alternativeRouteSevereColor = typedArray.getColor(
      R.styleable.NavigationMapRoute_alternativeRouteSevereCongestionColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_alternative_congestion_red));
    alternativeRouteShieldColor = typedArray.getColor(
      R.styleable.NavigationMapRoute_alternativeRouteShieldColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_alternative_shield_color));
    alternativeRouteScale = typedArray.getFloat(
      R.styleable.NavigationMapRoute_alternativeRouteScale, 1.0f);

    // Arrow color attributes
    arrowColor = typedArray.getColor(R.styleable.NavigationMapRoute_upcomingManeuverArrowColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_upcoming_maneuver_arrow_color));
    arrowBorderColor = typedArray.getColor(R.styleable.NavigationMapRoute_upcomingManeuverArrowBorderColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_upcoming_maneuver_arrow_border_color));

    // Waypoint attributes
    originWaypointIcon = typedArray.getResourceId(
      R.styleable.NavigationMapRoute_originWaypointIcon, R.drawable.ic_route_origin);
    destinationWaypointIcon = typedArray.getResourceId(
      R.styleable.NavigationMapRoute_destinationWaypointIcon, R.drawable.ic_route_destination);

    typedArray.recycle();
  }

  /**
   * Iterate through map style layers backwards till the first not-symbol layer is found.
   */
  private void placeRouteBelow() {
    if (belowLayer == null || belowLayer.isEmpty()) {
      List<Layer> styleLayers = mapboxMap.getLayers();
      if (styleLayers == null) {
        return;
      }
      for (int i = 0; i < styleLayers.size(); i++) {
        if (!(styleLayers.get(i) instanceof SymbolLayer)
          // Avoid placing the route on top of the user location layer
          && !styleLayers.get(i).getId().contains("mapbox-location")) {
          belowLayer = styleLayers.get(i).getId();
        }
      }
    }
  }

  private void drawWaypointMarkers(@NonNull MapboxMap mapboxMap, @Nullable Drawable originMarker,
                                   @Nullable Drawable destinationMarker) {
    if (originMarker == null || destinationMarker == null) {
      return;
    }
    SymbolLayer waypointLayer = mapboxMap.getLayerAs(RouteConstants.WAYPOINT_LAYER_ID);
    if (waypointLayer == null) {
      Bitmap bitmap = MapImageUtils.getBitmapFromDrawable(originMarker);
      mapboxMap.addImage("originMarker", bitmap);
      bitmap = MapImageUtils.getBitmapFromDrawable(destinationMarker);
      mapboxMap.addImage("destinationMarker", bitmap);

      waypointLayer = new SymbolLayer(RouteConstants.WAYPOINT_LAYER_ID, RouteConstants.WAYPOINT_SOURCE_ID)
        .withProperties(PropertyFactory.iconImage(match(
          Expression.toString(get("waypoint")), literal("originMarker"),
          stop("origin", literal("originMarker")),
          stop("destination", literal("destinationMarker"))
          )
          ),
          PropertyFactory.iconSize(interpolate(
            exponential(1.5f), zoom(),
            stop(22f, 2.8f),
            stop(12f, 1.3f),
            stop(10f, 0.8f),
            stop(0f, 0.6f)
          )),
          PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
          PropertyFactory.iconAllowOverlap(true),
          PropertyFactory.iconIgnorePlacement(true)
        );
      layerIds.add(RouteConstants.WAYPOINT_LAYER_ID);
      MapUtils.addLayerToMap(mapboxMap, waypointLayer, belowLayer);
    }
  }

  private Feature getPointFromLineString(RouteLeg leg, int index) {
    Feature feature = Feature.fromGeometry(Point.fromLngLat(
      leg.steps().get(index).maneuver().location().longitude(),
      leg.steps().get(index).maneuver().location().latitude()
    ));
    feature.addStringProperty(RouteConstants.SOURCE_KEY, RouteConstants.WAYPOINT_SOURCE_ID);
    feature.addStringProperty("waypoint",
      index == 0 ? "origin" : "destination"
    );
    return feature;
  }

  private void initialize() {
    getAttributes();
    placeRouteBelow();
    routeArrow = new MapRouteArrow(mapView, mapboxMap, arrowColor, arrowBorderColor);
  }

  private void addListeners() {
    mapboxMap.addOnMapClickListener(mapRouteClickListener);
    if (navigation != null) {
      navigation.addProgressChangeListener(progressChangeListener);
    }
    mapView.addOnMapChangedListener(this);
  }
}