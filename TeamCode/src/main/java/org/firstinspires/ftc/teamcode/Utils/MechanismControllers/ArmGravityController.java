package org.firstinspires.ftc.teamcode.Utils.MechanismControllers;

import org.firstinspires.ftc.teamcode.Utils.MathUtils.LookUpTable;

/**
 * a profiled arm controller that controls the arm
 * features:
 *  1. compensates for gravity according to a look-up table
 *  2. schedules a trapezoid movement to the targeted position
 *  3. when the targeted position of the schedule is reached, allows small adjustments of the targeted position, this will not be scheduled and will be executed right away
 * */
public class ArmGravityController implements MechanismController {
    /**
     *  the pid controller lib
     *  notice that it is only used for static control, we have taken over the trapezoid scheduling part
     *  */
    private final EnhancedPIDController enhancedPIDController;
    /**
     * the current schedule of the arm, this is used only to move the arm greatly, small adjustments will not be scheduled
     * */
    private EnhancedPIDController.TrapezoidPathSchedule currentSchedule;
    /**
     * the current desired position, updated if and if only great changes are applied to the desired position
     * */
    private double desiredPosition;
    /**
     * false when initialized,
     * true when the first task is specified
     * */
    private boolean alive;
    /** the profile of the arm, automatically obtained from robotConfig */
    public ArmProfile profile;
    /**
     * previousTimeMillis is to calculate dt
     * current schedule created time is the time when the last big movement is ordered
     * */
    private long previousTimeMillis, currentScheduleCreatedTime;

    private final double startingPosition;

    public ArmGravityController(ArmProfile armProfile) {
        this(armProfile, 0);
    }
    public ArmGravityController(ArmProfile armProfile, double startingPosition) {
        this.profile = armProfile;
        this.enhancedPIDController = new EnhancedPIDController(armProfile.staticPIDProfile);
        this.startingPosition = startingPosition;
    }

    /**
     * @param newDesiredPosition the new desired position, in encoder ticks
     * */
    public void updateDesiredPosition(double newDesiredPosition) {
        this.currentSchedule = new EnhancedPIDController.TrapezoidPathSchedule(profile.dynamicalPIDProfile, new EnhancedPIDController.Task(EnhancedPIDController.Task.TaskType.GO_TO_POSITION, newDesiredPosition), currentSchedule.getCurrentPathPosition(0), currentSchedule.getCurrentSpeed(0));
        this.alive = true;
    }

    /**
     * @param newDesiredPosition the new desired position, in encoder ticks
     * */
    public void goToDesiredPosition(double newDesiredPosition) {
        if (desiredPosition == newDesiredPosition) return;
        /* if there is no schedule yet */
        if (currentSchedule == null)
            this.currentSchedule = new EnhancedPIDController.TrapezoidPathSchedule(profile.dynamicalPIDProfile, new EnhancedPIDController.Task(EnhancedPIDController.Task.TaskType.GO_TO_POSITION, newDesiredPosition), startingPosition, 0);
        else {
            /* override the current schedule, but we start from the current desired position */
            final double scheduleTimer = (System.currentTimeMillis() - currentScheduleCreatedTime) / 1000.0;
            System.out.println("schedule timer: " + scheduleTimer);
            this.currentSchedule = new EnhancedPIDController.TrapezoidPathSchedule(profile.dynamicalPIDProfile, new EnhancedPIDController.Task(EnhancedPIDController.Task.TaskType.GO_TO_POSITION, newDesiredPosition), currentSchedule.getCurrentPathPosition(scheduleTimer), currentSchedule.getCurrentSpeed(scheduleTimer));
        }
        this.desiredPosition = newDesiredPosition;

        this.alive = true;
        currentScheduleCreatedTime = previousTimeMillis = System.currentTimeMillis();
        enhancedPIDController.reset(currentSchedule.getCurrentPathPosition(0), false);
    }

    @Override
    public double getMotorPower(double mechanismVelocity, double mechanismPosition) {
        if (Math.abs(enhancedPIDController.getErrorAccumulation()) > this.profile.errorAccumulationMax)
            enhancedPIDController.setErrorAccumulation(Math.copySign(this.profile.errorAccumulationMax, enhancedPIDController.getErrorAccumulation()));
        if (!alive) return 0;

        if (Math.abs(mechanismVelocity) > profile.encoderVelocityTrustedRange)
            mechanismVelocity = Math.copySign(profile.encoderVelocityTrustedRange, mechanismVelocity);
        final double scheduleTimer = (System.currentTimeMillis() - currentScheduleCreatedTime) / 1000.0 + profile.inAdvanceTime,
                currentDesiredPositionAccordingToSchedule = currentSchedule.getCurrentPathPosition(scheduleTimer),
                gravityCorrectionPower = profile.gravityTorqueEquilibriumMotorPowerLookUpTable.getYPrediction(mechanismPosition),
                dt = (System.currentTimeMillis() - previousTimeMillis) / 1000.0,
                pidCorrectionPower = enhancedPIDController.getMotorPowerGoToPositionClassic(mechanismPosition, mechanismVelocity,
                        new EnhancedPIDController.Task(EnhancedPIDController.Task.TaskType.GO_TO_POSITION, currentDesiredPositionAccordingToSchedule),
                        dt),
                overallCorrectionPower = gravityCorrectionPower + pidCorrectionPower;

        previousTimeMillis = System.currentTimeMillis();
        if (Math.abs(overallCorrectionPower) > profile.staticPIDProfile.getMaxPowerAllowed())
            return Math.copySign(profile.staticPIDProfile.getMaxPowerAllowed(), overallCorrectionPower);

        return overallCorrectionPower;
    }

    /**
     * for debugging only
     *  */
    @Deprecated
    public double getErrorAccumulation() {
        return this.enhancedPIDController.getErrorAccumulation();
    }

    public double getDesiredPosition() {
        if (this.currentSchedule != null)
            return this.currentSchedule.getCurrentPathPosition(999);
        return this.desiredPosition;
    }

    public void updateArmProfile(ArmProfile newArmProfile) {
        this.profile = newArmProfile;
        this.enhancedPIDController.setPidProfile(newArmProfile.staticPIDProfile);
    }

    public void reset(double initialPosition) {
        this.enhancedPIDController.reset(initialPosition, false);
    }


    public static final class ArmProfile {
        public final LookUpTable gravityTorqueEquilibriumMotorPowerLookUpTable;
        public final EnhancedPIDController.StaticPIDProfile staticPIDProfile;
        public final EnhancedPIDController.DynamicalPIDProfile dynamicalPIDProfile;
        public final double inAdvanceTime, errorAccumulationMax, encoderVelocityTrustedRange;

        /**
         * Creates a arm PID profile which is another dynamic pid profile
         *
         * @param maxPowerAllowed                       the restriction on power
         * @param errorTolerance                        the amount of error to ignore
         * @param maxAcceleration                       the maximum instant acceleration that the mechanism can achieve with the max power
         * @param maxVelocity                           the restriction on the velocity of the mechanism
         */
        public ArmProfile(double maxPowerAllowed, double errorStartDecelerate, double minPowerToMove, double errorTolerance, double feedForwardTime, double encoderVelocityTrustedRange, double integralCoefficient, double errorAccumulationMax, double maxAcceleration, double maxVelocity, double inAdvanceTime, LookUpTable gravityTorqueEquilibriumMotorPowerLookUpTable) {
            this.dynamicalPIDProfile = new EnhancedPIDController.DynamicalPIDProfile(Double.POSITIVE_INFINITY, maxPowerAllowed, minPowerToMove, errorTolerance, integralCoefficient, 0, maxAcceleration, maxVelocity);
            this.staticPIDProfile = new EnhancedPIDController.StaticPIDProfile(Double.POSITIVE_INFINITY, maxPowerAllowed, minPowerToMove, errorStartDecelerate, errorTolerance, feedForwardTime, integralCoefficient, 0);
            this.gravityTorqueEquilibriumMotorPowerLookUpTable = gravityTorqueEquilibriumMotorPowerLookUpTable;
            this.inAdvanceTime = inAdvanceTime;
            this.encoderVelocityTrustedRange = encoderVelocityTrustedRange;
            this.errorAccumulationMax = errorAccumulationMax;
        }
    }
}
