package app.organicmaps.routeghost;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.Router;
import app.organicmaps.sdk.routing.JunctionInfo;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.routing.RoutingInfo;
import app.organicmaps.sdk.routing.TransitRouteInfo;
import app.organicmaps.sdk.routing.TransitStepInfo;
import java.util.ArrayList;
import static app.organicmaps.routeghost.RouteData.*;

public final class RouteGhostUi {
    private static final String LAUNCH_LABEL = "◉  GPS";
    private static final int MAX_ATTEMPTS = 12;
    private static final int RETRY_DELAY_MS = 250;

    private RouteGhostUi() { }

    public static void attach(Activity activity) {
        FrameLayout frame = activity.findViewById(android.R.id.content);
        if (frame.findViewWithTag("routeghost-control") != null) return;

        TextView launch = new TextView(activity);
        launch.setTag("routeghost-control");
        launch.setText(LAUNCH_LABEL);
        launch.setTextColor(0xFFE6DEFF);
        launch.setTextSize(15);
        launch.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        launch.setGravity(Gravity.CENTER);
        launch.setElevation(dp(activity, 9));
        launch.setPadding(dp(activity, 18), dp(activity, 13), dp(activity, 18), dp(activity, 13));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1F1C3A);
        bg.setStroke(dp(activity, 2), 0xFFA88BFF);
        bg.setCornerRadius(dp(activity, 26));
        launch.setBackground(bg);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.BOTTOM);
        params.rightMargin = dp(activity, 15);
        params.bottomMargin = dp(activity, 98);
        frame.addView(launch, params);

        Handler handler = new Handler(Looper.getMainLooper());
        launch.setOnClickListener(v -> {
            if (!launch.isEnabled()) return;
            launch.setEnabled(false);
            launch.setText("Маршрут…");
            captureWithRetry(activity, launch, handler, 0);
        });
    }

    private static void captureWithRetry(Activity activity, TextView launch, Handler handler, int attempt) {
        if (activity.isFinishing() || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;

        RoutingController controller = RoutingController.get();
        boolean built = controller.isBuilt() || Framework.nativeIsRouteBuilt();
        boolean building = controller.isBuilding() || Framework.nativeIsRouteBuilding();
        boolean captured = false;
        String error = null;

        if (built && !building) {
            try {
                captured = captureSelectedRoute(activity);
            } catch (Exception ex) {
                error = ex.getMessage();
            }
        }

        if (!captured && attempt < MAX_ATTEMPTS && (building || built)) {
            handler.postDelayed(() -> captureWithRetry(activity, launch, handler, attempt + 1), RETRY_DELAY_MS);
            return;
        }

        if (error != null) {
            Toast.makeText(activity, "Не удалось прочитать маршрут: " + error, Toast.LENGTH_LONG).show();
        }
        launch.setText(LAUNCH_LABEL);
        launch.setEnabled(true);
        Intent intent = new Intent(activity, RouteGhostPanel.class);
        intent.putExtra("built", built || building);
        intent.putExtra("captured", captured);
        intent.putExtra("capture_attempted", true);
        activity.startActivity(intent);
    }

    private static int dp(Activity a, int value) {
        return (int) (value * a.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static boolean captureSelectedRoute(Activity activity) throws Exception {

        JunctionInfo[] junctions = Framework.nativeGetRouteJunctionPoints(50.0);
        if (junctions == null || junctions.length < 2) return false;

        ArrayList<Pt> points = new ArrayList<>();
        for (JunctionInfo junction : junctions) {
            if (junction == null) continue;
            Pt point;
            try {
                point = new Pt(junction.mLat, junction.mLon);
            } catch (IllegalArgumentException ex) {
                throw new Exception("Маршрут содержит неверные координаты");
            }
            if (points.isEmpty() || metres(points.get(points.size() - 1), point) > 0.15) {
                points.add(point);
            }
            if (points.size() > 100000) throw new Exception("Слишком длинный маршрут");
        }
        if (points.size() < 2 || length(points) < 5) return false;

        Router router = Router.get();
        String type = router == Router.Vehicle ? CAR
            : router == Router.Pedestrian ? WALK
            : router == Router.Bicycle ? OTHER : BUS;
        int seconds = (int) Math.max(1, length(points) / (WALK.equals(type) ? 1.35 : 10.0));
        Plan plan = new Plan();
        plan.title = "Маршрут Organic Maps";
        plan.source = "Organic Maps";
        plan.note = "";
        boolean split = false;
        if (router == Router.Transit) {
            TransitRouteInfo info = RoutingController.get().getCachedTransitInfo();
            if (info != null) {
                seconds = Math.max(1, info.getTotalTime());
                split = addTransitLegs(plan, points, info);
                if (split) {
                    plan.note = "Участки взяты из шагов маршрута Organic Maps; границы между пешим и транспортным участками приблизительные.";
                }
            }
        } else {
            RoutingInfo info = RoutingController.get().getCachedRoutingInfo();
            if (info != null && info.totalTimeInSeconds > 0) seconds = info.totalTimeInSeconds;
        }
        if (!split) {
            if (router == Router.Transit) {
                plan.note = "Не удалось разделить маршрут на участки: один тип движения на весь путь.";
            }
            plan.legs.add(new Leg(type, "Маршрут", points, seconds));
        }

        RouteGhostStorage.save(activity, plan);
        return true;
    }

    private static String legType(String name) {
        switch (name) {
            case "PEDESTRIAN": return WALK;
            case "SUBWAY": return METRO;
            case "TRAIN":
            case "LIGHT_RAIL": return TRAIN;
            case "MONORAIL": return MONORAIL;
            case "TRAM":
            case "CABLE_TRAM": return TRAM;
            case "BUS": return BUS;
            case "TROLLEYBUS": return TROLLEY;
            case "FERRY":
            case "WATER_SERVICE": return FERRY;
            case "FUNICULAR":
            case "AERIAL_LIFT":
            case "AIR_SERVICE": return OTHER;
            default: return null;
        }
    }

    private static Pt pointAt(ArrayList<Pt> points, double[] cum, double distance) {
        int last = points.size() - 1;
        if (distance <= 0) return points.get(0);
        if (distance >= cum[last]) return points.get(last);
        int idx = 1;
        while (idx < last && cum[idx] < distance) idx++;
        double span = Math.max(0.001, cum[idx] - cum[idx - 1]);
        return mix(points.get(idx - 1), points.get(idx), (distance - cum[idx - 1]) / span);
    }

    private static boolean addTransitLegs(Plan plan, ArrayList<Pt> points, TransitRouteInfo info) {
        ArrayList<String> types = new ArrayList<>();
        ArrayList<Integer> times = new ArrayList<>();
        for (TransitStepInfo step : info.getTransitSteps()) {
            String t = legType(step.getType().name());
            int s = step.getTimeInSec();
            if (t == null || s <= 0) continue;
            int last = types.size() - 1;
            if (last >= 0 && WALK.equals(t) && WALK.equals(types.get(last))) {
                times.set(last, times.get(last) + s);
            } else {
                types.add(t);
                times.add(s);
            }
        }
        if (types.isEmpty()) return false;

        double total = length(points);
        double walkMetres = 0;
        int transitSeconds = 0;
        for (int i = 0; i < types.size(); i++) {
            if (WALK.equals(types.get(i))) walkMetres += times.get(i) * 1.3;
            else transitSeconds += times.get(i);
        }
        double transitSpeed = transitSeconds > 0 && total > walkMetres ? (total - walkMetres) / transitSeconds : 8.0;
        double[] weight = new double[types.size()];
        double weightSum = 0;
        for (int i = 0; i < weight.length; i++) {
            weight[i] = times.get(i) * (WALK.equals(types.get(i)) ? 1.3 : transitSpeed);
            weightSum += weight[i];
        }
        if (weightSum <= 0) return false;

        double[] cum = new double[points.size()];
        for (int i = 1; i < cum.length; i++) cum[i] = cum[i - 1] + metres(points.get(i - 1), points.get(i));

        ArrayList<Leg> legs = new ArrayList<>();
        double from = 0;
        Pt startPoint = points.get(0);
        for (int i = 0; i < weight.length; i++) {
            boolean lastLeg = i == weight.length - 1;
            double to = lastLeg ? cum[cum.length - 1] : from + total * weight[i] / weightSum;
            Pt endPoint = lastLeg ? points.get(points.size() - 1) : pointAt(points, cum, to);
            ArrayList<Pt> part = new ArrayList<>();
            part.add(startPoint);
            for (int k = 0; k < points.size(); k++) {
                if (cum[k] > from + 0.01 && cum[k] < to - 0.01) part.add(points.get(k));
            }
            part.add(endPoint);
            if (length(part) >= 1) {
                String type = types.get(i);
                legs.add(new Leg(type, display(type), part, times.get(i)));
            }
            from = to;
            startPoint = endPoint;
        }
        if (legs.isEmpty()) return false;
        plan.legs.addAll(legs);
        return true;
    }
}
