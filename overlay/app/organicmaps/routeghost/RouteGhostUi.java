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
    private static final int MAX_ATTEMPTS = 12;
    private static final int RETRY_DELAY_MS = 250;

    private RouteGhostUi() { }

    public static void attach(Activity activity) {
        FrameLayout frame = activity.findViewById(android.R.id.content);
        if (frame.findViewWithTag("routeghost-control") != null) return;

        TextView launch = new TextView(activity);
        launch.setTag("routeghost-control");
        launch.setText("GPS  ◉");
        launch.setTextColor(Color.WHITE);
        launch.setTextSize(15);
        launch.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        launch.setGravity(Gravity.CENTER);
        launch.setElevation(dp(activity, 9));
        launch.setPadding(dp(activity, 18), dp(activity, 13), dp(activity, 18), dp(activity, 13));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF168454);
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
        launch.setText("GPS  ◉");
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
        // This JNI snapshot is tied to the selected alternative (not route marks).
        // The native fallback added by apply_patch.py can also read its stored RouteBase.
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
        if (router == Router.Transit) {
            TransitRouteInfo info = RoutingController.get().getCachedTransitInfo();
            if (info != null) {
                seconds = Math.max(1, info.getTotalTime());
                for (TransitStepInfo step : info.getTransitSteps()) {
                    String t = step.getType().name();
                    if (t.equals("SUBWAY")) { type = METRO; break; }
                    if (t.equals("TRAM")) { type = TRAM; break; }
                    if (t.equals("TROLLEYBUS")) { type = TROLLEY; break; }
                    if (t.equals("TRAIN") || t.equals("LIGHT_RAIL")) { type = TRAIN; break; }
                    if (t.equals("BUS")) { type = BUS; break; }
                }
            }
        } else {
            RoutingInfo info = RoutingController.get().getCachedRoutingInfo();
            if (info != null && info.totalTimeInSeconds > 0) seconds = info.totalTimeInSeconds;
        }

        Plan plan = new Plan();
        plan.title = "Маршрут Organic Maps";
        plan.source = "Organic Maps";
        plan.note = router == Router.Transit
            ? "Геометрия выбранного варианта; скорость и пересадки моделируются приблизительно."
            : "";
        plan.legs.add(new Leg(type, "Маршрут", points, seconds));
        // Persist only after validating the entire selected path.
        RouteGhostStorage.save(activity, plan);
        return true;
    }
}
