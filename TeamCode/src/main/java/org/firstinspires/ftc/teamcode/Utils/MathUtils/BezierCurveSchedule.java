package org.firstinspires.ftc.teamcode.Utils.MathUtils;

import org.firstinspires.ftc.teamcode.RobotConfig;

public class BezierCurveSchedule {
    private final double chassisAccelerationConstrain, chassisVelocityConstrain;
    private final BezierCurve path;
    private double t;
    public BezierCurveSchedule(double chassisAccelerationConstrain, double chassisVelocityConstrain, BezierCurve path) {
        this.chassisAccelerationConstrain = chassisAccelerationConstrain;
        this.chassisVelocityConstrain = chassisVelocityConstrain;
        this.path = path;
        t = 0;
    }

    /**
     * @param dt in seconds
     * @return the t value, scaled
     * */
    public double nextCheckPoint(double dt) {
        /* we need to scale the time by a factor such that the velocity and acceleration both don't exceed our constraint */

        /** the velocity and acceleration at the current point on the path, if we don't scale the time  */
        final double currentPointOriginalVelocity = path.getVelocityWithLERP(t).getMagnitude(),
                currentPointOriginalAcceleration = path.getAccelerationWithLERP(t).getMagnitude(),
                timeScaleFactor = Math.min(chassisVelocityConstrain / currentPointOriginalVelocity, chassisAccelerationConstrain / currentPointOriginalAcceleration);
        return t = Math.min(t + dt * timeScaleFactor, 1);
    }

    public double getT() {
        return t;
    }

    public Vector2D getPositionWithLERP() {
        return getPositionWithLERP(t);
    }
    public Vector2D getPositionWithLERP(double t) {
        return path.getPositionWithLERP(t);
    }
    public Vector2D getVelocityWithLERP() {
        return getVelocityWithLERP(t);
    }

    public Vector2D getVelocityWithLERP(double t) {
        return path.getVelocityWithLERP(t);
    }

    public boolean isCurrentPathFinished() {
        return t >= 1;
    }

    public static BezierCurveSchedule generateTranslationalSchedule(Vector2D startingPoint, Vector2D endingPoint) {
        return generateTranslationalSchedule(new BezierCurve(startingPoint, endingPoint));
    }

    public static BezierCurveSchedule generateTranslationalSchedule(Vector2D startingPoint, Vector2D midPoint, Vector2D endingPoint) {
        return generateTranslationalSchedule(new BezierCurve(startingPoint, midPoint, endingPoint));
    }

    public static BezierCurveSchedule generateTranslationalSchedule(BezierCurve path) {
        return new BezierCurveSchedule(RobotConfig.ChassisConfigs.autoStageMaxAcceleration, RobotConfig.ChassisConfigs.autoStageMaxVelocity, path);
    }

    public static double getTimeNeededToFinishRotationalSchedule(double startingRotation, double endingRotation) {
        return Math.abs(AngleUtils.getActualDifference(startingRotation, endingRotation)) / RobotConfig.ChassisConfigs.autoStageMaxAngularVelocity + 0.05; // add debug time
    }
}
