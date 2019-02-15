/**
 * This is a very simple robot program that can be used to send telemetry to
 * the data_logger script to characterize your drivetrain. If you wish to use
 * your actual robot code, you only need to implement the simple logic in the
 * autonomousPeriodic function and change the NetworkTables update rate
 *
 * This program assumes that you are using TalonSRX motor controllers and that
 * the drivetrain encoders are attached to the TalonSRX
 */

package dc;

import java.util.function.Supplier;

import com.ctre.phoenix.motorcontrol.FeedbackDevice;
import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;

import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.RobotController;
import edu.wpi.first.wpilibj.SpeedControllerGroup;
import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.drive.DifferentialDrive;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

public class Robot extends TimedRobot {

	static private double WHEEL_DIAMETER = 0.5;
	static private double ENCODER_PULSE_PER_REV = 4096;
	static private int PIDIDX = 0;

	Joystick stick;
	DifferentialDrive drive;

	WPI_TalonSRX leftFrontMotor;
	WPI_TalonSRX rightFrontMotor;

	Supplier<Double> leftEncoderPosition;
	Supplier<Double> leftEncoderRate;
	Supplier<Double> rightEncoderPosition;
	Supplier<Double> rightEncoderRate;

	NetworkTableEntry autoSpeedEntry = NetworkTableInstance.getDefault().getEntry("/robot/autospeed");
	NetworkTableEntry telemetryEntry = NetworkTableInstance.getDefault().getEntry("/robot/telemetry");

	double priorAutospeed = 0;
	Number[] numberArray = new Number[9];

	@Override
	public void robotInit() {

		stick = new Joystick(0);

		leftFrontMotor = new WPI_TalonSRX(3);
		leftFrontMotor.setInverted(true);
		leftFrontMotor.setSensorPhase(false);
		leftFrontMotor.setNeutralMode(NeutralMode.Brake);

		rightFrontMotor = new WPI_TalonSRX(6);
		rightFrontMotor.setInverted(true);
		rightFrontMotor.setSensorPhase(true);
		rightFrontMotor.setNeutralMode(NeutralMode.Brake);

		// left rear follows front
		WPI_TalonSRX leftRearMotor = new WPI_TalonSRX(1);
		leftRearMotor.setInverted(true);
		leftRearMotor.setSensorPhase(true);
		leftRearMotor.follow(leftFrontMotor);
		leftRearMotor.setNeutralMode(NeutralMode.Brake);

		// right rear follows front
		WPI_TalonSRX rightRearMotor = new WPI_TalonSRX(4);
		rightRearMotor.setInverted(true);
		rightRearMotor.setSensorPhase(true);
		rightRearMotor.follow(rightFrontMotor);
		rightRearMotor.setNeutralMode(NeutralMode.Brake);


		//
		// Configure drivetrain movement
		//

		SpeedControllerGroup leftGroup = new SpeedControllerGroup(leftFrontMotor, leftRearMotor);
		SpeedControllerGroup rightGroup = new SpeedControllerGroup(rightFrontMotor, rightRearMotor);

		drive = new DifferentialDrive(leftGroup, rightGroup);
		drive.setDeadband(0);


		//
		// Configure encoder related functions -- getDistance and getrate should return
		// ft and ft/s
		//

//		double encoderConstant = (1 / ENCODER_PULSE_PER_REV) * WHEEL_DIAMETER * Math.PI;
		double encoderConstant = (1 / ENCODER_PULSE_PER_REV) * 16;

		leftFrontMotor.configSelectedFeedbackSensor(FeedbackDevice.QuadEncoder, PIDIDX, 10);
		leftEncoderPosition = () -> leftFrontMotor.getSelectedSensorPosition(PIDIDX) * encoderConstant;
		leftEncoderRate = () -> leftFrontMotor.getSelectedSensorVelocity(PIDIDX) * encoderConstant * 10;

		rightFrontMotor.configSelectedFeedbackSensor(FeedbackDevice.QuadEncoder, PIDIDX, 10);
		rightEncoderPosition = () -> rightFrontMotor.getSelectedSensorPosition(PIDIDX) * encoderConstant;
		rightEncoderRate = () -> rightFrontMotor.getSelectedSensorVelocity(PIDIDX) * encoderConstant * 10;

		// Reset encoders
		leftFrontMotor.setSelectedSensorPosition(0);
		rightFrontMotor.setSelectedSensorPosition(0);

		// Set the update rate instead of using flush because of a ntcore bug
		// -> probably don't want to do this on a robot in competition
		NetworkTableInstance.getDefault().setUpdateRate(0.010);
	}

	@Override
	public void disabledInit() {
		System.out.println("Robot disabled");
		drive.tankDrive(0, 0);
	}

	@Override
	public void disabledPeriodic() {
	}

	@Override
	public void robotPeriodic() {
		// feedback for users, but not used by the control program
		SmartDashboard.putNumber("l_encoder_pos", leftEncoderPosition.get());
		SmartDashboard.putNumber("l_encoder_rate", leftEncoderRate.get());
		SmartDashboard.putNumber("r_encoder_pos", rightEncoderPosition.get());
		SmartDashboard.putNumber("r_encoder_rate", rightEncoderRate.get());
	}

	@Override
	public void teleopInit() {
		System.out.println("Robot in operator control mode");
	}

	@Override
	public void teleopPeriodic() {
		drive.arcadeDrive(-stick.getY(), stick.getX());
	}

	@Override
	public void autonomousInit() {
		System.out.println("Robot in autonomous mode");
	}

	double lastRight = 0;
	double lastLeft = 0;
	double lastTime = 0;

	/**
	 * If you wish to just use your own robot program to use with the data logging
	 * program, you only need to copy/paste the logic below into your code and
	 * ensure it gets called periodically in autonomous mode
	 *
	 * Additionally, you need to set NetworkTables update rate to 10ms using the
	 * setUpdateRate call.
	 */
	@Override
	public void autonomousPeriodic() {

		// Retrieve values to send back before telling the motors to do something
		double now = Timer.getFPGATimestamp();

		double leftPosition = leftEncoderPosition.get();
		double leftRate = leftEncoderRate.get();

		double rightPosition = rightEncoderPosition.get();
		double rightRate = rightEncoderRate.get();

		double battery = RobotController.getBatteryVoltage();

		double leftMotorVolts = leftFrontMotor.getMotorOutputVoltage();
		double rightMotorVolts = rightFrontMotor.getMotorOutputVoltage();

		double time = System.currentTimeMillis() * 1e-3;
		double dt = time - lastTime;
		double dl = leftPosition - lastLeft;
		double dr = rightPosition - lastRight;

		lastTime = time;
		lastLeft = leftPosition;
		lastRight = rightPosition;

		System.out.println(
				"Left (" + leftRate + ", " + (dl / .02) + ", " + (dl / dt) + ")" + 
				"Right (" + rightRate + ", " + (dr / .02) + ", " + (dr / dt) + ")" + dt);

		// Retrieve the commanded speed from NetworkTables
		double autospeed = autoSpeedEntry.getDouble(0);
		priorAutospeed = autospeed;

		autospeed = 1;

		// command motors to do things
		drive.tankDrive(autospeed, autospeed, false);

		// send telemetry data array back to NT
		numberArray[0] = now;
		numberArray[1] = battery;
		numberArray[2] = autospeed;
		numberArray[3] = leftMotorVolts;
		numberArray[4] = rightMotorVolts;
		numberArray[5] = leftPosition;
		numberArray[6] = rightPosition;
		numberArray[7] = leftRate;
		numberArray[8] = rightRate;

		telemetryEntry.setNumberArray(numberArray);
	}
}
